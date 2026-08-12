import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateModule } from '@ngx-translate/core';

/**
 * Clickable prompt chips rendered at the end of the conversation thread by
 * {@code ChatMessagesComponent} (starter prompts on an empty thread, follow-ups after an answer).
 * Purely presentational: it receives i18n keys and emits the one that was clicked, so the full
 * {@code /chat} page and the floating widget share the same look through the same thread component.
 */
@Component({
  selector: 'jhi-chat-suggestions',
  templateUrl: './chat-suggestions.component.html',
  styleUrl: './chat-suggestions.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FaIconComponent, TranslateModule],
})
export class ChatSuggestionsComponent {
  /** i18n keys of the prompts to offer. */
  readonly suggestions = input.required<string[]>();
  /** Emits the i18n key of the clicked chip. */
  readonly suggestionSelected = output<string>();
}
