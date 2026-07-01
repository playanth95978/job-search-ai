import { ChangeDetectionStrategy, Component, ElementRef, effect, inject, signal, viewChild } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';

import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { map } from 'rxjs/operators';

import TranslateDirective from '../shared/language/translate.directive';
import { ChatHistoryComponent } from '../home/chat-history/chat-history';
import { JobCopilotService } from '../job-copilot/shared/job-copilot.service';
import { ChatMessageHistory } from '../job-copilot/shared/job-copilot.model';
import { ConversationService } from '../admin/conversation/service/conversation.service';
import { NewConversation } from '../admin/conversation/conversation.model';

/**
 * ChatGPT-style conversation pane for the tool-enabled Job Copilot agent. The sidebar
 * ({@link ChatHistoryComponent}) lists past conversations (jhi Conversation) and routes to
 * {@code /chat/:id}; this pane loads that conversation's messages and drives the exchange.
 */
@Component({
  selector: 'jhi-chat',
  templateUrl: './chat.html',
  styleUrl: './chat.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, FontAwesomeModule, TranslateModule, TranslateDirective, ChatHistoryComponent],
})
export default class Chat {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly service = inject(JobCopilotService);
  private readonly conversationService = inject(ConversationService);
  private readonly translateService = inject(TranslateService);

  readonly conversationId = toSignal(this.route.paramMap.pipe(map(params => params.get('id'))), { initialValue: null });

  readonly messages = signal<ChatMessageHistory[]>([]);
  readonly loading = signal(false);
  readonly sending = signal(false);
  draft = '';

  /** Prompts offered on an empty conversation. */
  readonly suggestions = [
    'chat.suggestions.search',
    'chat.suggestions.match',
    'chat.suggestions.applications',
    'chat.suggestions.coverLetter',
  ];

  private readonly thread = viewChild<ElementRef<HTMLElement>>('thread');
  /** Id whose history is already loaded, so navigating to a self-created chat doesn't wipe the thread. */
  private loadedId: string | null = null;

  constructor() {
    effect(() => {
      const id = this.conversationId();
      if (id && id !== this.loadedId) {
        this.loadedId = id;
        this.loadHistory(id);
      } else if (!id) {
        this.loadedId = null;
        this.messages.set([]);
      }
    });
  }

  send(): void {
    const text = this.draft.trim();
    if (!text || this.sending()) {
      return;
    }
    this.draft = '';
    this.append('USER', text);
    this.sending.set(true);

    const id = this.conversationId();
    if (id) {
      this.ask(id, text);
      return;
    }
    // New chat: create the conversation first, adopt its id (without reloading), then ask.
    this.conversationService.create({ id: null, title: text.slice(0, 60) } as NewConversation).subscribe({
      next: conv => {
        this.loadedId = conv.id;
        void this.router.navigate(['/chat', conv.id]);
        this.ask(conv.id, text);
      },
      error: () => this.fail(),
    });
  }

  useSuggestion(key: string): void {
    this.draft = this.translateService.instant(key);
    this.send();
  }

  private ask(conversationId: string, text: string): void {
    this.service.sendAssistantMessage(conversationId, text).subscribe({
      next: res => {
        this.append('ASSISTANT', res.message);
        this.sending.set(false);
      },
      error: () => this.fail(),
    });
  }

  private loadHistory(id: string): void {
    this.loading.set(true);
    this.service.getConversationMessages(id).subscribe({
      next: msgs => {
        this.messages.set(msgs.filter(m => m.type !== 'SYSTEM'));
        this.loading.set(false);
        this.scrollToBottom();
      },
      error: () => {
        this.messages.set([]);
        this.loading.set(false);
      },
    });
  }

  private append(type: ChatMessageHistory['type'], content: string): void {
    this.messages.update(list => [
      ...list,
      { conversationId: this.conversationId() ?? '', content, type, timestamp: new Date().toISOString() },
    ]);
    this.scrollToBottom();
  }

  private fail(): void {
    this.sending.set(false);
    this.append('ASSISTANT', '⚠️');
  }

  private scrollToBottom(): void {
    setTimeout(() => {
      const el = this.thread()?.nativeElement;
      if (el) {
        el.scrollTop = el.scrollHeight;
      }
    });
  }
}
