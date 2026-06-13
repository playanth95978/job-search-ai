import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';

import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { TranslateModule } from '@ngx-translate/core';
import { map } from 'rxjs/operators';

import { ChatHistoryComponent } from '../home/chat-history/chat-history';

@Component({
  selector: 'jhi-chat',
  templateUrl: './chat.html',
  styleUrl: './chat.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FontAwesomeModule, TranslateModule, ChatHistoryComponent],
})
export default class Chat {
  private readonly route = inject(ActivatedRoute);

  readonly conversationId = toSignal(this.route.paramMap.pipe(map(params => params.get('id'))), { initialValue: null });
  readonly hasSelection = computed(() => this.conversationId() !== null);
}
