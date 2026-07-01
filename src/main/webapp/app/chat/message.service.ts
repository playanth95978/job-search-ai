import { inject, Injectable, signal } from '@angular/core';
import { auditTime, filter, map, Observable, startWith, tap } from 'rxjs';
import { HttpClient, HttpEvent, HttpEventType, HttpResponse } from '@angular/common/http';
import dayjs from 'dayjs/esm';
import { marked } from 'marked';
import hljs from 'highlight.js';
import { IConversation } from '../admin/conversation/conversation.model';
import { ConversationService } from '../admin/conversation/service/conversation.service';
import { ChatHistory } from '../home/chat-history/chat-history';

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
  events?: any[];
}

@Injectable({
  providedIn: 'root',
})
export class MessageService {
  type = signal<TypeChat | null>(null);
  private readonly http = inject(HttpClient);

  private readonly _ocrContext = signal<string | null>(null);

  get ocrContext(): string | null {
    return this._ocrContext();
  }

  setOcrContext(context: string | null): void {
    this._ocrContext.set(context);
  }

  private readonly _completeMessages = signal<ChatMessage[]>([
    {
      id: 'welcome',
      role: 'assistant',
      content: "Bonjour ! Comment puis-je vous aider aujourd'hui ?",
    },
  ]);

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
  private readonly _suggestions = signal<string[]>([]);
  readonly suggestions = this._suggestions.asReadonly();
  private streamBuffer = '';
  private readonly conversationService = inject(ConversationService);

  constructor() {
    marked.setOptions({
      gfm: true,
      breaks: true,
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
      auditTime(30),
      map((event: HttpEvent<string>): string =>
        event.type === HttpEventType.DownloadProgress ? (event.partialText ?? '') : ((event as HttpResponse<string>).body ?? ''),
      ),
    );
  }

  getHistory(id: string): Observable<ChatHistory[]> {
    return this.http.get<ChatHistory[]>(`/api/chat/history/${id}`).pipe(
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
    this._completeMessages.set([
      {
        id: 'welcome',
        role: 'assistant',
        content: "Bonjour ! Comment puis-je vous aider aujourd'hui ?",
      },
    ]);
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

    stream$.subscribe({
      next: message => {
        const parseResult = this.parseSSEWithMeta(message.content);
        this.streamBuffer = parseResult.text;

        if (parseResult.sources.length > 0) {
          this._pendingSources.set(parseResult.sources);
        }

        this._streamingMessage.set(this.streamBuffer);
        this._streamingHtml.set(this.renderStreamingMarkdown(this.streamBuffer));
      },

      complete: () => {
        const html = marked.parse(this.streamBuffer, { async: false });
        const sources = this._pendingSources();

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
          };

          return copy;
        });
        if (this.isNewMessage()) {
          this.createNewConversation(this.currentConversationId()!, prompt);
        }
        this.generateSuggestions(this.streamBuffer);
        this.streamBuffer = '';
        this._streamingMessage.set('');
        this._streamingHtml.set('');
        this._pendingSources.set([]);
        this._generatingInProgress.set(false);
      },

      error: () => {
        this._streamingMessage.set('');
        this._streamingHtml.set('');
        this._pendingSources.set([]);
        this._generatingInProgress.set(false);
      },
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
        auditTime(30),
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

  private parseSSEWithMeta(fullText: string): { text: string; sources: SourceInfo[] } {
    if (!fullText) return { text: '', sources: [] };

    const events = fullText.split('\n\n');
    let result = '';
    let sources: SourceInfo[] = [];

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
              choices?: { delta?: { content?: string } }[];
            };

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
    return { text: result, sources };
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

  private generateSuggestions(response: string): void {
    if (!response || response.length < 50) {
      this._suggestions.set([]);
      return;
    }
    const suggestions: string[] = [];
    const lower = response.toLowerCase();
    if (lower.includes('jira') || lower.includes('ticket')) {
      suggestions.push('Quels sont les tickets Jira lies a ce sujet ?');
    }
    if (lower.includes('code') || lower.includes('class') || lower.includes('service') || lower.includes('java')) {
      suggestions.push('Montre-moi le code source associe');
    }
    if (lower.includes('confluence') || lower.includes('documentation') || lower.includes('page')) {
      suggestions.push("Y a-t-il d'autres pages Confluence sur ce sujet ?");
    }
    if (lower.includes('etape') || lower.includes('procedure') || lower.includes('comment')) {
      suggestions.push('Peux-tu detailler les etapes ?');
    }
    if (suggestions.length === 0) {
      suggestions.push('Peux-tu approfondir ce sujet ?');
      suggestions.push('Quelles sont les sources associees ?');
    }
    this._suggestions.set(suggestions.slice(0, 3));
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
    let url: string;
    if (this.type() !== null) {
      url = `/api/chat/${this.type()?.toLocaleLowerCase()}?message=${encodeURIComponent(prompt)}`;
      if (this.type() === 'PDF' && ocrContext) {
        url += `&ocrContext=${encodeURIComponent(ocrContext)}`;
      }
    } else {
      url = `/api/chat/merge?message=${encodeURIComponent(prompt)}`;
    }
    if (conversationId) {
      url += `&conversationId=${encodeURIComponent(conversationId)}`;
    }

    return this.http
      .get(url, {
        responseType: 'text',
        observe: 'events',
        reportProgress: true,
      })
      .pipe(
        filter(
          (event: HttpEvent<string>): boolean => event.type === HttpEventType.DownloadProgress || event.type === HttpEventType.Response,
        ),
        auditTime(30),
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
