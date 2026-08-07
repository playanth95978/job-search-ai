import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { A11yModule } from '@angular/cdk/a11y';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { TranslateModule } from '@ngx-translate/core';
import { TranslateDirective } from 'app/shared/language';

import { MessageService } from '../message.service';
import { ChatMessagesComponent } from '../chat-messages/chat-messages.component';
import { ChatInputAreaComponent } from '../chat-input-area/chat-input-area.component';

/**
 * Floating, always-available chat launcher. A FAB in the bottom-right corner opens a compact panel
 * (docked on desktop, full-width bottom-sheet on mobile) that reuses the shared {@link MessageService}
 * singleton and the same {@link ChatMessagesComponent}/{@link ChatInputAreaComponent} as the full
 * {@code /chat} page — so a conversation started here is the same conversation shown there.
 * Rendered globally in the main layout and only for authenticated users.
 */
@Component({
  selector: 'jhi-chat-widget',
  templateUrl: './chat-widget.component.html',
  styleUrl: './chat-widget.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [A11yModule, FaIconComponent, TranslateModule, TranslateDirective, ChatMessagesComponent, ChatInputAreaComponent, NgbTooltip],
})
export class ChatWidgetComponent {
  readonly open = signal(false);
  protected readonly messageService = inject(MessageService);

  readonly messages = this.messageService.messages;
  readonly streamingMessage = this.messageService.streamingMessage;
  readonly streamingHtml = this.messageService.streamingHtml;
  readonly isTyping = this.messageService.generatingInProgress;

  readonly formatMessage = (message: string): string => this.messageService.formatCodeBlocks(message);

  toggle(): void {
    this.open.update(v => !v);
  }

  close(): void {
    this.open.set(false);
  }

  startNewChat(): void {
    this.messageService.clearMessages();
  }
}
