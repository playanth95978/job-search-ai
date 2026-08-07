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
  signal,
  ViewChild,
  ViewChildren,
} from '@angular/core';
import dayjs from 'dayjs/esm';
import { HttpClient } from '@angular/common/http';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { TranslateModule } from '@ngx-translate/core';
import { SanitizerComponent } from '../../shared/sanitizer/sanitizer/sanitizer.component';
import { ChatMessage } from '../message.service';
import { FormatMediumDatetimePipe } from '../../shared/date';
import TranslateDirective from '../../shared/language/translate.directive';

@Component({
  selector: 'jhi-chat-messages',
  templateUrl: './chat-messages.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrls: ['./chat-messages.component.scss'],
  imports: [FontAwesomeModule, FormatMediumDatetimePipe, SanitizerComponent, TranslateModule, TranslateDirective],
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

  /** Affiche le bouton « revenir en bas » quand l'utilisateur a remonté le fil. */
  readonly showScrollToBottom = signal(false);

  /** Vrai tant que l'utilisateur suit le bas du fil (auto-scroll actif). */
  private stickToBottom = true;

  /** Tolérance en px pour considérer qu'on est « en bas » (marge d'arrondi / inertie). */
  private static readonly STICK_THRESHOLD_PX = 80;

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
    // On ne recolle en bas que si l'utilisateur y était déjà : sinon on le ramènerait de force
    // pendant qu'il relit un message plus haut (comportement ChatGPT/Gemini).
    if (this.stickToBottom) {
      this.scrollToBottom();
    }
  }

  /**
   * Suivi de la position de lecture. Dès que l'utilisateur remonte, on cesse de suivre le flux ;
   * on réactive le suivi dès qu'il revient au ras du bas.
   */
  onScroll(): void {
    const el = this.scrollContainer?.nativeElement;
    if (!el) return;
    const distanceFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight;
    const atBottom = distanceFromBottom <= ChatMessagesComponent.STICK_THRESHOLD_PX;
    this.stickToBottom = atBottom;
    this.showScrollToBottom.set(!atBottom);
  }

  /** Bouton « revenir en bas » : réactive le suivi automatique du flux. */
  scrollToBottomSmooth(): void {
    const el = this.scrollContainer?.nativeElement;
    if (!el) return;
    this.stickToBottom = true;
    this.showScrollToBottom.set(false);
    el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' });
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
