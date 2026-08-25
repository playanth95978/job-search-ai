import { computed, inject, Injectable, signal } from '@angular/core';
import { auditTime, filter, map, Observable, startWith, Subscription, tap } from 'rxjs';
import { HttpClient, HttpEvent, HttpEventType, HttpResponse } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import dayjs from 'dayjs/esm';
import { marked } from 'marked';
import hljs from 'highlight.js';
import { IConversation } from '../admin/conversation/conversation.model';
import { ConversationService } from '../admin/conversation/service/conversation.service';
import { ChatHistory } from '../home/chat-history/chat-history';
import { ChatAction } from './chat-actions/chat-action.model';

export type TypeChat = null | 'CODE' | 'JIRA' | 'CONFLUENCE' | 'PDF';

export interface SourceInfo {
  title: string;
  source: string;
  url: string;
  score: number;
  // Renseignés uniquement pour les sources de code (macros VBA) : permettent d'ouvrir le canvas
  // de code sur le bon module à la bonne ligne.
  kind?: 'code' | null;
  module?: string | null;
  startLine?: number | null;
  endLine?: number | null;
  viewUrl?: string | null;
}

export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  generating?: boolean;
  streamingMessage?: string;
  isStreaming?: boolean;
  sources?: SourceInfo[];
  feedback?: 'up' | 'down' | null;
  /**
   * Boutons proposés à la fin du tour, décidés par le backend d'après ce que les outils de l'agent
   * ont réellement fait. Absent pour une réponse purement conversationnelle.
   */
  actions?: ChatAction[];
}

@Injectable({
  providedIn: 'root',
})
export class MessageService {
  type = signal<TypeChat | null>(null);
  private readonly http = inject(HttpClient);
  private readonly translate = inject(TranslateService);

  /** Fallback greeting, shown only if the i18n bundle isn't loaded yet when this root service is built. */
  private static readonly WELCOME_FALLBACK = "Bonjour ! Comment puis-je vous aider aujourd'hui ?";

  /** Localized greeting shown as the first assistant message of an empty conversation. */
  private welcomeMessage(): ChatMessage {
    return {
      id: 'welcome',
      role: 'assistant',
      content: this.resolveWelcome(),
    };
  }

  /** Resolves the welcome text, guarding against the raw key when translations aren't loaded yet. */
  private resolveWelcome(): string {
    const text = this.translate.instant('chat.welcome');
    return typeof text === 'string' && text && text !== 'chat.welcome' && !text.startsWith('translation-not-found')
      ? text
      : MessageService.WELCOME_FALLBACK;
  }

  private readonly _ocrContext = signal<string | null>(null);

  get ocrContext(): string | null {
    return this._ocrContext();
  }

  setOcrContext(context: string | null): void {
    this._ocrContext.set(context);
  }

  private readonly _completeMessages = signal<ChatMessage[]>([this.welcomeMessage()]);

  private readonly _messages = signal<ChatMessage[]>(this._completeMessages());
  private readonly _generatingInProgress = signal<boolean>(false);
  private readonly _currentConversationId = signal<string | null>(null);
  readonly currentConversationId = this._currentConversationId.asReadonly();

  readonly messages = this._messages.asReadonly();
  readonly generatingInProgress = this._generatingInProgress.asReadonly();
  private readonly _isStreaming = signal<boolean>(false);
  private readonly _isNewMessage = signal<boolean>(false);
  private readonly _streamingMessage = signal<string>('');
  private readonly _streamingHtml = signal<string>('');
  readonly isStreaming = this._isStreaming.asReadonly();
  readonly isNewMessage = this._isNewMessage.asReadonly();
  readonly streamingMessage = this._streamingMessage.asReadonly();
  readonly streamingHtml = this._streamingHtml.asReadonly();
  private readonly _pendingSources = signal<SourceInfo[]>([]);
  /**
   * Actions du tour en cours, en attente de la fin du flux.
   *
   * Séparé de `_pendingSources` parce que les deux n'arrivent pas au même moment : les sources
   * peuvent tomber en cours de route, les actions ne sont émises qu'après le dernier token.
   */
  private readonly _pendingActions = signal<ChatAction[]>([]);
  private readonly _suggestions = signal<string[]>([]);
  readonly suggestions = this._suggestions.asReadonly();

  /** Prompts offered on an empty conversation (i18n keys). */
  private static readonly STARTER_PROMPTS = [
    'chat.suggestions.search',
    'chat.suggestions.match',
    'chat.suggestions.applications',
    'chat.suggestions.coverLetter',
  ];

  /**
   * Chips to display under the thread, as i18n keys: starter prompts while the user hasn't asked
   * anything yet, follow-ups once an answer has been produced, and nothing while the agent replies.
   * Shared by the {@code /chat} page and the floating widget so both behave the same.
   */
  readonly promptChips = computed<string[]>(() => {
    if (this._generatingInProgress()) {
      return [];
    }
    const followUps = this._suggestions();
    if (followUps.length > 0) {
      return followUps;
    }
    return this._messages().some(m => m.role === 'user') ? [] : MessageService.STARTER_PROMPTS;
  });
  private streamBuffer = '';
  private readonly conversationService = inject(ConversationService);

  // --- Moteur de révélation progressive ("goutte à goutte") ---------------------------------
  // Le réseau livre le texte par paquets irréguliers (SSE + auditTime), ce qui donne un rendu
  // saccadé. On découple donc l'affichage de l'arrivée réseau : le flux remplit `targetText`,
  // et une boucle rAF fait avancer le texte affiché caractère par caractère à cadence régulière,
  // comme ChatGPT/Gemini. La vitesse s'adapte au retard accumulé pour ne jamais décrocher.
  private readonly _targetText = signal<string>('');
  private readonly _revealedChars = signal<number>(0);
  private revealHandle: number | null = null;
  private readonly _streamEnded = signal<boolean>(false);
  private onRevealComplete: (() => void) | null = null;
  private lastMarkdownRender = 0;
  private streamSubscription: Subscription | null = null;

  /** Nombre de caractères révélés par frame, proportionnel au retard (min 1). */
  private static readonly REVEAL_DIVISOR = 4;
  /** Le markdown est re-parsé au plus toutes les 16 ms (~60 fps) pour une fluidité maximale. */
  private static readonly MARKDOWN_RENDER_INTERVAL_MS = 16;

  constructor() {
    marked.setOptions({
      gfm: true,
      breaks: true,
    });

    // This root service can be constructed before the i18n bundle is loaded, so translate.instant()
    // at field-init may miss and render the raw key. Keep the welcome message in sync once the
    // translation becomes available and on every language change, while the conversation is empty.
    this.translate.stream('chat.welcome').subscribe((text: string) => {
      if (!text || text === 'chat.welcome' || text.startsWith('translation-not-found')) return;
      const patch = (msgs: ChatMessage[]): ChatMessage[] =>
        msgs.length === 1 && msgs[0].id === 'welcome' ? [{ ...msgs[0], content: text }] : msgs;
      this._completeMessages.update(patch);
      this._messages.update(patch);
    });
  }

  /**
   * Generic SSE helper reused by the specialised agents (Jira, Playwright, ...).
   * Streams the cumulative response text of an SSE endpoint, emitting the full
   * buffer on each progress tick — callers parse it with their own event parser.
   */
  streamText(url: string): Observable<string> {
    return this.http.get(url, { responseType: 'text', observe: 'events', reportProgress: true }).pipe(
      filter((event: HttpEvent<string>): boolean => event.type === HttpEventType.DownloadProgress || event.type === HttpEventType.Response),
      auditTime(16),
      map((event: HttpEvent<string>): string =>
        event.type === HttpEventType.DownloadProgress ? (event.partialText ?? '') : ((event as HttpResponse<string>).body ?? ''),
      ),
    );
  }

  getHistory(id: string): Observable<ChatHistory[]> {
    return this.http.get<ChatHistory[]>(`/api/chat-history/history/${encodeURIComponent(id)}`).pipe(
      map(history =>
        history.map(h => ({
          ...h,
          timestamp: dayjs(h.timestamp),
        })),
      ),
    );
  }

  loadHistory(id: string): Observable<ChatHistory[]> {
    this.setCurrentConversationId(id);
    return this.getHistory(id).pipe(
      tap(history => {
        this.setMessages(history);
      }),
    );
  }

  setMessages(history: ChatHistory[]): void {
    const messages: ChatMessage[] = history.map((h, index) => ({
      id: (h.conversationId ?? '') + '-' + index.toString(),
      role: h.type === 'USER' ? 'user' : 'assistant',
      content: h.type === 'USER' ? h.content : marked.parse(h.content, { async: false }),
    }));
    this._completeMessages.set(messages);
    this._messages.set(messages);
  }

  setCurrentConversationId(id: string | null): void {
    this._currentConversationId.set(id);
    if (!id) {
      this.clearMessages();
    }
  }

  clearMessages(): void {
    this._completeMessages.set([this.welcomeMessage()]);
    this._messages.set(this._completeMessages());
    this._currentConversationId.set(null);
  }

  sendMessage(prompt: string, isCode: boolean, file?: File): void {
    this._generatingInProgress.set(true);
    if (!this.currentConversationId()) {
      this._currentConversationId.set(window.crypto.randomUUID());
      this._isNewMessage.set(true);
    }

    this.streamBuffer = '';

    // 1. Add user message AND initial assistant placeholder
    this._messages.update(msgs => [
      ...msgs,
      {
        id: this.currentConversationId()!,
        content: prompt,
        role: 'user',
        isStreaming: false,
      },
      {
        id: this.currentConversationId()! + '-assistant',
        content: '',
        role: 'assistant',
        isStreaming: true,
      },
    ]);

    // 2. Stream response
    const stream$ = file
      ? this.uploadFileAndChat(file, prompt, this._currentConversationId())
      : this.getChatResponseStream(prompt, isCode, this._currentConversationId(), this._ocrContext());

    this._pendingSources.set([]);
    this._suggestions.set([]);

    this.resetReveal();

    this.streamSubscription = stream$.subscribe({
      next: message => {
        const parseResult = this.parseSSEWithMeta(message.content);
        this.streamBuffer = parseResult.text;

        if (parseResult.sources.length > 0) {
          this._pendingSources.set(parseResult.sources);
        }

        // Retenues jusqu'à la fin du flux : les poser tout de suite ferait apparaître les boutons
        // au milieu d'une phrase en cours d'écriture.
        this._pendingActions.set(parseResult.actions);

        // On alimente la cible ; c'est la boucle rAF qui décide du rythme d'affichage.
        this._targetText.set(this.streamBuffer);
        this.startReveal();
      },

      complete: () => {
        // On ne finalise pas tout de suite : on laisse la révélation rattraper son retard,
        // sinon la fin du message apparaîtrait d'un bloc.
        this._streamEnded.set(true);
        this.onRevealComplete = () => {
          this.finalizeAssistantMessage(this.streamBuffer, this._pendingSources(), this._pendingActions());
          if (this.isNewMessage()) {
            this.createNewConversation(this.currentConversationId()!, prompt);
            // Sans ce reset, chaque message suivant recréait la même conversation :
            // le flag restait à true pour toute la durée du fil.
            this._isNewMessage.set(false);
          }
          this.generateSuggestions(this.streamBuffer);
          this.streamBuffer = '';
          this.streamSubscription = null;
          this.resetReveal();
          this._streamingMessage.set('');
          this._streamingHtml.set('');
          this._pendingSources.set([]);
          this._pendingActions.set([]);
          this._generatingInProgress.set(false);
        };
        this.startReveal();
      },

      error: () => {
        this.resetReveal();
        this.streamSubscription = null;
        this.streamBuffer = '';
        this._streamingMessage.set('');
        this._streamingHtml.set('');
        this._pendingSources.set([]);
        this._generatingInProgress.set(false);
      },
    });
  }

  /**
   * Démarre la boucle de révélation. À chaque frame, on avance de quelques caractères vers le
   * texte reçu : plus le retard est grand, plus on avance vite, de sorte que l'affichage reste
   * fluide sans jamais décrocher du flux réseau.
   */
  private startReveal(): void {
    if (this.revealHandle !== null) return;

    const tick = (): void => {
      const targetText = this._targetText();
      const revealedChars = this._revealedChars();
      const backlog = targetText.length - revealedChars;

      if (backlog > 0) {
        const step = Math.max(1, Math.ceil(backlog / MessageService.REVEAL_DIVISOR));
        const newRevealed = Math.min(targetText.length, revealedChars + step);
        this._revealedChars.set(newRevealed);
        const shown = targetText.slice(0, newRevealed);
        this._streamingMessage.set(shown);

        // Le parse markdown est throttlé : c'est l'opération coûteuse, et 30 fps suffisent
        // largement à l'œil. On force le rendu final quand tout est révélé.
        const now = performance.now();
        const caughtUp = newRevealed >= targetText.length;
        if (caughtUp || now - this.lastMarkdownRender >= MessageService.MARKDOWN_RENDER_INTERVAL_MS) {
          this.lastMarkdownRender = now;
          this._streamingHtml.set(this.renderStreamingMarkdown(shown));
        }
      }

      // Le flux est terminé ET tout le texte est affiché : on peut finaliser le message.
      if (this._streamEnded() && this._revealedChars() >= this._targetText().length) {
        this.stopReveal();
        const done = this.onRevealComplete;
        this.onRevealComplete = null;
        done?.();
        return;
      }

      this.revealHandle = requestAnimationFrame(tick);
    };

    this.revealHandle = requestAnimationFrame(tick);
  }

  private stopReveal(): void {
    if (this.revealHandle !== null) {
      cancelAnimationFrame(this.revealHandle);
      this.revealHandle = null;
    }
  }

  private resetReveal(): void {
    this.stopReveal();
    this._targetText.set('');
    this._revealedChars.set(0);
    this._streamEnded.set(false);
    this.onRevealComplete = null;
    this.lastMarkdownRender = 0;
  }

  /**
   * Interrompt la génération en cours (bouton « Stop », comme ChatGPT). Le texte déjà révélé
   * est conservé et figé dans la conversation — on ne perd pas la réponse partielle.
   */
  stopGeneration(): void {
    if (!this._generatingInProgress()) return;

    this.streamSubscription?.unsubscribe();
    this.streamSubscription = null;
    this.stopReveal();

    // On fige ce qui a été effectivement affiché, pas ce qui a été reçu.
    const partial = this._targetText().slice(0, this._revealedChars());
    // Aucune action sur un tour interrompu : un bouton tire d'une reponse a moitie ecrite
    // serait trompeur.
    this.finalizeAssistantMessage(partial, this._pendingSources());
    this._pendingActions.set([]);
    this.resetReveal();
    this.streamBuffer = '';
    this._streamingMessage.set('');
    this._streamingHtml.set('');
    this._pendingSources.set([]);
    this._generatingInProgress.set(false);
  }

  /** Remplace le placeholder assistant en cours par le message final rendu en HTML. */
  private finalizeAssistantMessage(text: string, sources: SourceInfo[], actions: ChatAction[] = []): void {
    const html = marked.parse(text, { async: false });
    this._messages.update(msgs => {
      const copy = [...msgs];
      const lastIndex = copy.length - 1;
      copy[lastIndex] = {
        ...copy[lastIndex],
        content: html,
        id: this.currentConversationId() ?? '',
        isStreaming: false,
        role: 'assistant',
        sources: sources.length > 0 ? sources : undefined,
        // `undefined` et non `[]` : le template teste la longueur, et une liste vide ferait rendre
        // un conteneur vide qui consommerait l'espacement du fil.
        actions: actions.length > 0 ? actions : undefined,
      };
      return copy;
    });
  }

  setType(type: TypeChat): void {
    this.type.set(type);
    if (type !== 'PDF') {
      this.setOcrContext(null);
    }
  }

  formatCodeBlocks(content: string): string {
    if (!content) return '';
    // wrap les blocs code

    content = content.replace(/<pre><code class="language-(\w+)">([\s\S]*?)<\/code><\/pre>/g, (match, lang, code) => {
      const decoded = this.decodeHtml(code);
      let highlighted: string;
      if (lang === 'mermaid' || lang === 'promql') {
        highlighted = this.escapeHtml(decoded);
      } else {
        try {
          highlighted = hljs.highlight(decoded, { language: lang }).value;
        } catch {
          highlighted = hljs.highlightAuto(decoded).value;
        }
      }

      let formattedCode = '';
      if (lang === 'mermaid') {
        formattedCode = `
        <div class="code-container mb-4">
          <div class="mermaid">
            ${decoded}
          </div>
        </div>
      `;
      } else {
        formattedCode = ` <div class="code-body">
            <pre class="m-0"><code class="language-${lang}" hljs>${highlighted}</code></pre>
          </div>
        </div>`;
      }

      const uniqueId = `code-${Math.random().toString(36).substring(2, 9)}`;
      return (
        `
        <div class="code-container modern-code mb-4" id="${uniqueId}">
          <div class="code-header d-flex justify-content-between align-items-center py-2 px-3">
            <div class="d-flex align-items-center gap-2">
              <span class="code-lang-badge">${lang}</span>
            </div>
            <button
              jhiAppClickLogger
              id="copy-code-btn"
              class="btn btn-link btn-sm text-decoration-none copy-code-btn d-flex align-items-center gap-1"
              onclick="const container = document.getElementById('${uniqueId}'); const code = container.querySelector('code').innerText; navigator.clipboard.writeText(code); const btn = this; const originalHtml = btn.innerHTML; btn.innerHTML = '<fa-icon icon=check></fa-icon> <span>Copié !</span>'; btn.classList.add('text-success'); setTimeout(() => { btn.innerHTML = originalHtml; btn.classList.remove('text-success'); }, 2000);">
              <fa-icon icon="copy"></fa-icon>
              <span>Copier</span>
            </button>
          </div>` + formattedCode
      );
    });

    return content;
  }

  private escapeHtml(code: string): string {
    return code.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#039;');
  }

  private decodeHtml(html: string): string {
    return new DOMParser().parseFromString(html, 'text/html').documentElement.textContent || '';
  }

  public createNewConversation(messageId: string, prompt: string): void {
    const newConversation: IConversation = {
      id: messageId,
      title: prompt,
      version: 0,
    };
    this.conversationService.createNewChat(newConversation).subscribe();
  }

  uploadFileForOCR(file: File): Observable<any> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<any>('/api/mistral-ocr/process', formData);
  }

  uploadFileAndChat(file: File, query: string, conversationId: string | null): Observable<ChatMessage> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('query', query);

    const url = `/api/mistral-ocr/chat`;

    return this.http
      .post(url, formData, {
        responseType: 'text',
        observe: 'events',
        reportProgress: true,
      })
      .pipe(
        filter(
          (event: HttpEvent<string>): boolean => event.type === HttpEventType.DownloadProgress || event.type === HttpEventType.Response,
        ),
        auditTime(16),
        map(
          (event: HttpEvent<string>): ChatMessage =>
            event.type === HttpEventType.DownloadProgress
              ? {
                  id: conversationId ?? 'new',
                  content: event.partialText!,
                  role: 'assistant',
                  generating: true,
                }
              : {
                  id: conversationId ?? 'new',
                  content: (event as HttpResponse<string>).body!,
                  role: 'assistant',
                  generating: false,
                },
        ),
        startWith<ChatMessage>({
          id: conversationId ?? 'new',
          content: '',
          role: 'assistant',
          generating: true,
        }),
      );
  }

  /**
   * Parse the current streaming buffer as Markdown while the SSE stream is
   * still in progress. Because chunks can arrive mid-token (e.g. inside a
   * fenced code block or a list item), we auto-close any open fenced code
   * block before calling `marked` so the partial rendering stays coherent.
   */
  renderStreamingMarkdown(buffer: string): string {
    if (!buffer) return '';
    const safeBuffer = this.closeOpenCodeFence(buffer);
    return marked.parse(safeBuffer, { async: false });
  }

  private closeOpenCodeFence(text: string): string {
    const fenceMatches = text.match(/```/g);
    const fenceCount = fenceMatches ? fenceMatches.length : 0;
    if (fenceCount % 2 === 1) {
      const suffix = text.endsWith('\n') ? '```' : '\n```';
      return text + suffix;
    }
    return text;
  }

  private parseSSE(fullText: string): string {
    return this.parseSSEWithMeta(fullText).text;
  }

  private parseSSEWithMeta(fullText: string): { text: string; sources: SourceInfo[]; actions: ChatAction[] } {
    if (!fullText) return { text: '', sources: [], actions: [] };

    const events = fullText.split('\n\n');
    let result = '';
    let sources: SourceInfo[] = [];
    let actions: ChatAction[] = [];

    for (const event of events) {
      if (!event.trim()) continue;

      const lines = event.split('\n');
      for (const line of lines) {
        const trimmed = line.trimStart();
        if (trimmed.startsWith('data:')) {
          const payload = trimmed.slice(5).trim();
          if (!payload || payload === '[DONE]') continue;

          try {
            const json = JSON.parse(payload) as {
              content?: string;
              type?: string;
              actions?: ChatAction[];
              choices?: { delta?: { content?: string } }[];
            };

            // Les actions voyagent dans leur propre champ, pas encodées en JSON dans `content` :
            // les deux backends émettent la même trame, et une liste vide est émise malgré tout
            // pour distinguer « ce tour n'a pas d'action » de « la trame n'est jamais arrivée ».
            if (json?.type === 'actions') {
              actions = json.actions ?? [];
              continue;
            }

            if (json?.type === 'sources' && json?.content) {
              try {
                sources = JSON.parse(json.content) as SourceInfo[];
              } catch {
                // ignore malformed sources
              }
              continue;
            }

            if (json?.type === 'start' || json?.type === 'end') {
              continue;
            }

            const content = json?.content ?? json?.choices?.[0]?.delta?.content ?? '';
            result += content || '';
          } catch {
            result += payload;
          }
        }
      }
    }
    return { text: result, sources, actions };
  }

  /**
   * Sends the prompt behind a suggestion chip. Chips carry i18n keys (so they follow the UI
   * language); the text actually sent to the agent is the translated sentence.
   */
  sendSuggestion(key: string): void {
    this.useSuggestion(this.translate.instant(key));
  }

  useSuggestion(suggestion: string): void {
    this._suggestions.set([]);
    if (suggestion.includes('Confluence')) {
      this.setType('CONFLUENCE');
    } else if (suggestion.includes('Jira')) {
      this.setType('JIRA');
    } else if (suggestion.includes('code source')) {
      this.setType('CODE');
    }
    this.sendMessage(suggestion, false);
  }

  setFeedback(messageId: string, feedback: 'up' | 'down'): void {
    this._messages.update(msgs =>
      msgs.map(m =>
        m.id === messageId || m.id === messageId + '-assistant' ? { ...m, feedback: m.feedback === feedback ? null : feedback } : m,
      ),
    );
  }

  /**
   * Derives follow-up chips from the answer that was just streamed. Keyword-based on purpose: it
   * costs no extra LLM call (which the free-tier quota would not appreciate) and stays predictable.
   * Matching is accent-insensitive because the agent answers in natural French.
   */
  private generateSuggestions(response: string): void {
    if (!response || response.length < 50) {
      this._suggestions.set([]);
      return;
    }
    const text = MessageService.deaccent(response);
    const suggestions: string[] = [];
    if (/\b(offre|poste|job|opportunit|recrut)/.test(text)) {
      suggestions.push('chat.suggestions.followUp.whyMatch');
    }
    if (/\b(candidatur|postul|appl(y|ication)|entretien|interview)/.test(text)) {
      suggestions.push('chat.suggestions.followUp.applications');
    }
    if (/\b(lettre|motivation|cover letter)/.test(text)) {
      suggestions.push('chat.suggestions.followUp.coverLetter');
    }
    if (/\b(cv|profil|competence|skill|experience)/.test(text)) {
      suggestions.push('chat.suggestions.followUp.improveCv');
    }
    if (suggestions.length === 0) {
      suggestions.push('chat.suggestions.followUp.moreDetails', 'chat.suggestions.followUp.bestOffers');
    }
    this._suggestions.set(suggestions.slice(0, 3));
  }

  private static deaccent(value: string): string {
    return value.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '');
  }

  exportConversation(): string {
    const msgs = this._messages();
    let md = '# Conversation\n\n';
    for (const msg of msgs) {
      if (msg.id === 'welcome') continue;
      const role = msg.role === 'user' ? 'Vous' : 'Assistant IA';
      const text = msg.role === 'assistant' ? this.stripHtml(msg.content) : msg.content;
      md += `## ${role}\n\n${text}\n\n---\n\n`;
    }
    return md;
  }

  private stripHtml(html: string): string {
    const doc = new DOMParser().parseFromString(html, 'text/html');
    return doc.body.textContent || '';
  }

  private getChatResponseStream(
    prompt: string,
    isCode: boolean,
    conversationId: string | null,
    ocrContext: string | null = null,
  ): Observable<ChatMessage> {
    // Tool-enabled Job Copilot agent: the message goes in the request body (text/plain) and the
    // conversation id in the path; the reply streams back as SSE (start / token* / end).
    const url = `/api/job-copilot/assistant/${encodeURIComponent(conversationId ?? '')}/stream`;

    return this.http
      .post(url, prompt, {
        responseType: 'text',
        observe: 'events',
        reportProgress: true,
        headers: { 'Content-Type': 'text/plain' },
      })
      .pipe(
        filter(
          (event: HttpEvent<string>): boolean => event.type === HttpEventType.DownloadProgress || event.type === HttpEventType.Response,
        ),
        auditTime(16),
        map(
          (event: HttpEvent<string>): ChatMessage =>
            event.type === HttpEventType.DownloadProgress
              ? {
                  id: conversationId!,
                  content: event.partialText!,
                  role: 'assistant',
                  generating: true,
                }
              : {
                  id: conversationId!,
                  content: (event as HttpResponse<string>).body!,
                  role: 'assistant',
                  generating: false,
                },
        ),
        startWith<ChatMessage>({
          id: conversationId!,
          content: '',
          role: 'assistant',
          generating: true,
        }),
      );
  }
}
