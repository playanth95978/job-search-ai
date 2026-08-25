import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateModule } from '@ngx-translate/core';

import { ChatAction } from './chat-action.model';

/**
 * Clickable actions rendered under an assistant message.
 *
 * Purely presentational, like {@link ChatSuggestionsComponent} next to it: it receives typed actions
 * and emits the one that was clicked. Deciding what a click *does* — resend a prompt, navigate,
 * call an API — belongs to the container, which is the only place holding the router and the HTTP
 * services. That is what lets the full `/chat` page and the floating widget share this component.
 *
 * The two are kept separate on purpose: suggestions are starter prompts owned by the front, actions
 * are decided by the backend from what the agent's tools actually did.
 */
@Component({
  selector: 'jhi-chat-actions',
  templateUrl: './chat-actions.component.html',
  styleUrl: './chat-actions.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FaIconComponent, TranslateModule],
})
export class ChatActionsComponent {
  /** Actions to offer; an empty list renders nothing. */
  readonly actions = input.required<ChatAction[]>();

  /**
   * Disables every button of this message.
   *
   * Set once an action of the message has been played: a thread is a history, and nothing would
   * tell the user they just applied twice.
   */
  readonly disabled = input<boolean>(false);

  /** Emits the clicked action, unchanged. */
  readonly actionSelected = output<ChatAction>();

  /**
   * Icon for an action, chosen on its type only.
   *
   * A per-key icon map would have to be extended every time the backend adds a label, and a missing
   * entry renders an empty square. The type is closed, so this switch is exhaustive by construction.
   */
  iconFor(action: ChatAction): IconProp {
    switch (action.type) {
      case 'prompt':
        return 'lightbulb';
      case 'navigate':
        return 'arrow-right';
      case 'mutate':
        // `file-alt` et non `file-lines` : seule la premiere est enregistree dans le bundle
        // d'icones, et une icone absente se rend en carre vide sans erreur.
        return action.operation === 'APPLY' ? 'paper-plane' : 'file-alt';
    }
  }
}
