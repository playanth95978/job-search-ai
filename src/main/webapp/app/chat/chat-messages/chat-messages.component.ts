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
import { ChatSuggestionsComponent } from '../chat-suggestions/chat-suggestions.component';
import { ChatActionsComponent } from '../chat-actions/chat-actions.component';
import { ChatAction } from '../chat-actions/chat-action.model';

@Component({
  selector: 'jhi-chat-messages',
  templateUrl: './chat-messages.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrls: ['./chat-messages.component.scss'],
  imports: [
    FontAwesomeModule,
    FormatMediumDatetimePipe,
    SanitizerComponent,
    TranslateModule,
    TranslateDirective,
    ChatSuggestionsComponent,
    ChatActionsComponent,
  ],
})
export class ChatMessagesComponent implements AfterViewChecked {
  messages = input.required<ChatMessage[]>();
  streamingMessage = input<string>('');
  streamingHtml = input<string>('');
  isTyping = input<boolean>(false);
  hasRag = input<boolean>(false);

  /**
   * Messages dont une action a déjà été jouée : leurs boutons sont désactivés.
   *
   * Porté ici et non dans `ChatMessage` parce que c'est un état d'affichage, pas une donnée de
   * conversation — il ne doit ni être persisté ni traverser le réseau.
   */
  readonly playedActionMessageIds = signal<ReadonlySet<string>>(new Set());
  formatMessage = input.required<(message: string) => string>();
  /** Puces de suggestion (clés i18n) affichées en fin de fil ; vide = rien à proposer. */
  suggestions = input<string[]>([]);
  feedbackClicked = output<{ messageId: string; feedback: 'up' | 'down' }>();
  suggestionSelected = output<string>();
  /** Action cliquée dans le fil ; le conteneur décide de ce qu'elle déclenche. */
  readonly actionSelected = output<ChatAction>();

  /**
   * Marque le message comme joué, puis remonte l'action.
   *
   * Le marquage est fait ici plutôt que dans le conteneur pour que le bouton se fige au clic, sans
   * attendre l'aller-retour réseau d'une action `mutate` : sinon rien n'empêche un double clic de
   * partir deux fois.
   */
  onActionSelected(messageId: string, action: ChatAction): void {
    this.playedActionMessageIds.update(played => new Set(played).add(messageId));
    this.actionSelected.emit(action);
  }

  expandedSources = signal<Record<string, boolean>>({});

  /** Affiche le bouton « revenir en bas » quand l'utilisateur a remonté le fil. */
  readonly showScrollToBottom = signal(false);

  /** Vrai tant que l'utilisateur suit le bas du fil (auto-scroll actif). */
  private stickToBottom = signal(true);

  /** Tolérance en px pour considérer qu'on est « en bas » (marge d'arrondi / inertie). */
  private static readonly STICK_THRESHOLD_PX = 2;

  private lastScrollHeight = 0;

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
    const el = this.scrollContainer?.nativeElement;
    if (!el) return;

    // On ne recolle en bas que si l'utilisateur y était déjà ET que le contenu a changé.
    // Cela évite de bloquer le scroll manuel quand le texte n'évolue pas ou quand l'utilisateur
    // essaie de remonter (scrollHeight inchangé entre deux frames de rendu).
    if (this.stickToBottom() && el.scrollHeight !== this.lastScrollHeight) {
      this.scrollToBottom();
    }
    this.lastScrollHeight = el.scrollHeight;
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
    this.stickToBottom.set(atBottom);
    this.showScrollToBottom.set(!atBottom);
  }

  /** Bouton « revenir en bas » : réactive le suivi automatique du flux. */
  scrollToBottomSmooth(): void {
    const el = this.scrollContainer?.nativeElement;
    if (!el) return;
    this.stickToBottom.set(true);
    this.showScrollToBottom.set(false);
    el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' });
  }

  toggleSources(messageId: string): void {
    this.expandedSources.update(sources => ({
      ...sources,
      [messageId]: !sources[messageId],
    }));
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
