import { ChangeDetectionStrategy, Component, ElementRef, inject, signal, viewChild, OnInit, OnDestroy } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { map } from 'rxjs/operators';
import { ChatHistoryComponent } from '../home/chat-history/chat-history';
import { JobCopilotService } from '../job-copilot/shared/job-copilot.service';
import { ConversationService } from '../admin/conversation/service/conversation.service';
import { StateStorageService } from '../core/auth/state-storage.service';
import { ChatInputAreaComponent } from './chat-input-area/chat-input-area.component';
import { ChatHeaderComponent } from './chat-header/chat-header.component';
import { ChatMessagesComponent } from './chat-messages/chat-messages.component';
import { ChatContainerWrapperComponent } from './chat-container-wrapper/chat-container-wrapper.component';
import { Subject, takeUntil } from 'rxjs';
import { AccountService } from '../core/auth/account.service';
import { MessageService, TypeChat } from './message.service';

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
  imports: [ChatHistoryComponent, ChatInputAreaComponent, ChatHeaderComponent, ChatMessagesComponent, ChatContainerWrapperComponent],
})
export default class Chat implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly messageService = inject(MessageService);

  readonly conversationId = toSignal(this.route.paramMap.pipe(map(params => params.get('id'))), { initialValue: null });

  readonly loading = signal(false);
  readonly sending = signal(false);
  readonly messages = this.messageService.messages;
  readonly streamingMessage = this.messageService.streamingMessage;
  readonly streamingHtml = this.messageService.streamingHtml;
  userInput = '';
  readonly isTyping = this.messageService.generatingInProgress;

  private readonly destroy$ = new Subject<void>();
  private readonly accountService = inject(AccountService);
  draft = '';

  /** Chips proposées sous le fil (amorces puis relances) — même source que le widget flottant. */
  readonly suggestions = this.messageService.promptChips;

  private readonly thread = viewChild<ElementRef<HTMLElement>>('thread');

  ngOnInit(): void {
    this.route.params.pipe(takeUntil(this.destroy$)).subscribe(params => {
      const id = params['id'];
      if (!id) {
        this.messageService.clearMessages();
        return;
      }
      this.loading.set(true);
      this.messageService.loadHistory(id).subscribe({
        next: () => {
          this.loading.set(false);
          this.scrollToBottom();
        },
        error: () => this.loading.set(false),
      });
    });
  }

  startNewChat(): void {
    this.messageService.clearMessages();
    this.router.navigate(['/chat']);
  }

  formatMessage = (message: string): string => {
    return this.messageService.formatCodeBlocks(message);
  };

  onFeedback(event: { messageId: string; feedback: 'up' | 'down' }): void {
    this.messageService.setFeedback(event.messageId, event.feedback);
  }

  onSuggestionClick(suggestion: string): void {
    this.messageService.sendSuggestion(suggestion);
  }

  login(): void {
    this.router.navigate(['/login']);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
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
