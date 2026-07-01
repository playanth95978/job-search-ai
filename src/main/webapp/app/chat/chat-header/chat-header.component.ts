import { ChangeDetectionStrategy, Component, output } from '@angular/core';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import TranslateDirective from '../../shared/language/translate.directive';

@Component({
  selector: 'jhi-chat-header',
  templateUrl: './chat-header.component.html',
  styleUrls: ['./chat-header.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FontAwesomeModule, TranslateDirective],
})
export class ChatHeaderComponent {
  exportClicked = output();
  historyClicked = output();
  newChatClicked = output();
}
