import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { TranslateModule } from '@ngx-translate/core';
import { MessageService, TypeChat } from '../message.service';

@Component({
  selector: 'jhi-chat-input-area',
  templateUrl: './chat-input-area.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrls: ['./chat-input-area.component.scss'],
  imports: [TranslateModule, FormsModule, FontAwesomeModule],
})
export class ChatInputAreaComponent {
  type = input<TypeChat | null>(null);
  userInput = signal<string>('');
  fileSelected = output<Event>();
  loginClicked = output();
  removeFileClicked = output<number>();
  sendClicked = output();
  userInputChange = output<string>();
  triggerUploadClicked = output<HTMLInputElement>();

  readonly isUploading = input<boolean>(false);

  /** Hauteur max du textarea avant scroll interne (~8 lignes). */
  private static readonly MAX_INPUT_HEIGHT_PX = 200;

  private readonly messageService = inject(MessageService);
  // @ts-ignore
  readonly isTyping = this.messageService.generatingInProgress;

  onUserInput(value: string): void {
    this.userInput.set(value);
    this.userInputChange.emit(value);
  }

  /** Entrée envoie le message ; Maj+Entrée insère un saut de ligne. */
  onEnter(event: Event): void {
    const keyboardEvent = event as KeyboardEvent;
    if (keyboardEvent.shiftKey) return;
    keyboardEvent.preventDefault();
    this.sendMessage();
  }

  /** Le textarea grandit avec le contenu, jusqu'à une hauteur max (au-delà : scroll interne). */
  autoGrow(textarea: HTMLTextAreaElement): void {
    textarea.style.height = 'auto';
    textarea.style.height = `${Math.min(textarea.scrollHeight, ChatInputAreaComponent.MAX_INPUT_HEIGHT_PX)}px`;
  }

  sendMessage(): void {
    if (this.isTyping()) return;

    if (!this.userInput().trim()) return;

    this.messageService.sendMessage(this.userInput(), false);
    this.userInput.set('');
  }

  /** Interrompt la génération en conservant le texte déjà affiché. */
  stopGeneration(): void {
    this.messageService.stopGeneration();
  }
}
