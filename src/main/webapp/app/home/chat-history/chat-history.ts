import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { RouterModule } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import dayjs from 'dayjs/esm';
import { Conversation } from '../../admin/conversation/list/conversation';
import { faPlus } from '@fortawesome/free-solid-svg-icons';
import TranslateDirective from '../../shared/language/translate.directive';

export interface ChatHistory {
  conversationId: string;
  content: string;
  type: string;
  timestamp: dayjs.Dayjs;
}

@Component({
  selector: 'jhi-chat-history',
  templateUrl: './chat-history.html',
  styleUrl: './chat-history.scss',
  standalone: true,
  imports: [CommonModule, FontAwesomeModule, RouterModule, Conversation, TranslateModule, TranslateDirective],
})
export class ChatHistoryComponent {
  protected readonly faPlus = faPlus;
}
