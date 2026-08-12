import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateModule } from '@ngx-translate/core';

/**
 * Clickable prompt chips shown under the conversation thread (starter prompts on an empty thread,
 * follow-ups after an answer). Purely presentational: it receives i18n keys and emits the one that
 * was clicked, so both the full {@code /chat} page and the floating widget share the same look.
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
  /** Compact variant for the floating widget: one scrollable row instead of a wrapping grid. */
  readonly compact = input(false);
  /** Emits the i18n key of the clicked chip. */
  readonly suggestionSelected = output<string>();
}
