import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language';
import { TranslateModule } from '@ngx-translate/core';
import { JobCopilotService } from '../shared/job-copilot.service';
import { JobOffer } from '../shared/job-copilot.model';
import { COVER_LETTER_LANGUAGES } from '../shared/apply-modal/apply-modal.component';
import { ConversationService } from '../../admin/conversation/service/conversation.service';
import { IConversation } from '../../admin/conversation/conversation.model';
import { Conversation } from '../../admin/conversation/list/conversation';

interface InterviewChatMessage {
  role: 'recruiter' | 'candidate';
  text: string;
}

@Component({
  selector: 'jhi-job-copilot-interview',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, FaIconComponent, TranslateDirective, TranslateModule, Conversation],
  templateUrl: './interview.component.html',
  styleUrl: './interview.component.scss',
})
export class InterviewComponent implements OnInit {
  private service = inject(JobCopilotService);

  jobOffers = signal<JobOffer[]>([]);
  selectedJobOfferId = signal('');
  conversationId = signal<string | null>(null);
  messages = signal<InterviewChatMessage[]>([]);
  answer = signal('');
  loading = signal(false);
  sending = signal(false);
  error = signal<string | null>(null);
  language = signal('');
  conversationService: ConversationService = inject(ConversationService);
  readonly languages = COVER_LETTER_LANGUAGES;

  ngOnInit(): void {
    this.service.getApplications().subscribe({
      next: apps => {
        const offers = apps.map(app => app.jobOffer).filter((offer): offer is JobOffer => !!offer?.id);
        this.jobOffers.set(offers);
        if (offers.length > 0) {
          this.selectedJobOfferId.set(offers[0].id!);
        }
      },
      error() {},
    });
  }

  start(): void {
    const jobOfferId = this.selectedJobOfferId();
    if (!jobOfferId) return;
    this.loading.set(true);
    this.error.set(null);
    this.messages.set([]);
    this.conversationId.set(null);
    this.service.startInterview(jobOfferId, this.language() || undefined).subscribe({
      next: res => {
        this.conversationId.set(res.conversationId);
        this.messages.set([{ role: 'recruiter', text: res.message }]);
        this.loading.set(false);
        this.createNewConversation(res.conversationId, res.message);
      },
      error: err => {
        this.error.set(err.error?.message ?? 'Failed to start the interview');
        this.loading.set(false);
      },
    });
  }

  public createNewConversation(messageId: string, prompt: string): void {
    const newConversation: IConversation = {
      id: messageId,
      title: this.selectedOffer()?.title + ' - Interview',
      version: 0,
      typeChat: 'INTERVIEW',
    };
    this.conversationService.createNewChat(newConversation).subscribe();
  }

  send(): void {
    const text = this.answer().trim();
    const conversationId = this.conversationId();
    const jobOfferId = this.selectedJobOfferId();
    if (!text || !conversationId || !jobOfferId || this.sending()) return;

    this.messages.update(msgs => [...msgs, { role: 'candidate', text }]);
    this.answer.set('');
    this.sending.set(true);
    this.error.set(null);

    this.service.sendInterviewAnswer(conversationId, jobOfferId, text, this.language() || undefined).subscribe({
      next: res => {
        this.messages.update(msgs => [...msgs, { role: 'recruiter', text: res.message }]);
        this.sending.set(false);
      },
      error: err => {
        this.error.set(err.error?.message ?? 'Failed to send your answer');
        this.sending.set(false);
      },
    });
  }

  /** Loads a past conversation (clicked in the history) into the interview chat. */
  openConversation(conversation: IConversation): void {
    if (!conversation.id || this.loading()) return;
    this.loading.set(true);
    this.error.set(null);
    this.service.getConversationMessages(conversation.id).subscribe({
      next: history => {
        this.messages.set(
          history
            .filter(m => m.type === 'USER' || m.type === 'ASSISTANT')
            .map(m => ({ role: m.type === 'USER' ? 'candidate' : 'recruiter', text: m.content })),
        );
        this.conversationId.set(conversation.id);
        this.loading.set(false);
      },
      error: err => {
        this.error.set(err.error?.message ?? 'Failed to load the conversation');
        this.loading.set(false);
      },
    });
  }

  reset(): void {
    this.conversationId.set(null);
    this.messages.set([]);
    this.answer.set('');
    this.error.set(null);
  }

  selectedOffer(): JobOffer | undefined {
    return this.jobOffers().find(o => o.id === this.selectedJobOfferId());
  }

  trackByIndex(index: number): number {
    return index;
  }
}
