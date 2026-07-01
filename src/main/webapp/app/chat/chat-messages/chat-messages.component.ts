import {
  AfterViewChecked,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  input,
  output,
  QueryList,
  Renderer2,
  ViewChild,
  ViewChildren,
} from '@angular/core';
import dayjs from 'dayjs/esm';
import { HttpClient } from '@angular/common/http';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { SanitizerComponent } from '../../shared/sanitizer/sanitizer/sanitizer.component';
import { ChatMessage } from '../message.service';
import { FormatMediumDatetimePipe } from '../../shared/date';

@Component({
  selector: 'jhi-chat-messages',
  templateUrl: './chat-messages.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrls: ['./chat-messages.component.scss'],
  imports: [FontAwesomeModule, FormatMediumDatetimePipe, SanitizerComponent],
})
export class ChatMessagesComponent implements AfterViewChecked {
  messages = input.required<ChatMessage[]>();
  streamingMessage = input<string>('');
  streamingHtml = input<string>('');
  isTyping = input<boolean>(false);
  hasRag = input<boolean>(false);

  hasEvents = input<boolean>(false);
  formatMessage = input.required<(message: string) => string>();
  feedbackClicked = output<{ messageId: string; feedback: 'up' | 'down' }>();

  expandedSources: Record<string, boolean> = {};

  @ViewChildren('messageEl') messageElements!: QueryList<ElementRef>;
  @ViewChildren('contentRef') contentRefs!: QueryList<ElementRef>;
  @ViewChild('scrollContainer') private scrollContainer!: ElementRef;
  @ViewChild('contentRef') contentRef!: ElementRef;
  private readonly renderer = inject(Renderer2);
  private readonly http = inject(HttpClient);

  get dateNow(): dayjs.Dayjs {
    return dayjs();
  }

  ngAfterViewChecked(): void {
    this.scrollToBottom();
  }

  toggleSources(messageId: string): void {
    this.expandedSources[messageId] = !this.expandedSources[messageId];
  }

  /** Une source pointant vers un fichier uploadé interne (vs une URL externe Confluence/GitHub). */
  isDownloadable(url: string | undefined): boolean {
    return !!url && url.startsWith('/api/files/');
  }

  onFeedback(messageId: string, feedback: 'up' | 'down'): void {
    this.feedbackClicked.emit({ messageId, feedback });
  }

  copyTextToClipboard(text: string, button?: HTMLElement): void {
    navigator.clipboard.writeText(text).then(() => {
      if (button) {
        const span = button.querySelector('span');
        const copyIcon = button.querySelector('.copy-icon') as HTMLElement;
        const checkIcon = button.querySelector('.check-icon') as HTMLElement;

        if (span) span.innerText = 'Copié !';
        if (copyIcon) copyIcon.style.display = 'none';
        if (checkIcon) checkIcon.style.display = 'inline';

        this.renderer.addClass(button, 'copied');

        setTimeout(() => {
          if (span) span.innerText = 'Copier';
          if (copyIcon) copyIcon.style.display = 'inline';
          if (checkIcon) checkIcon.style.display = 'none';
          this.renderer.removeClass(button, 'copied');
        }, 2000);
      }
    });
  }

  private scrollToBottom(): void {
    try {
      this.scrollContainer.nativeElement.scrollTop = this.scrollContainer.nativeElement.scrollHeight;
    } catch {
      // Ignore
    }
  }
}
