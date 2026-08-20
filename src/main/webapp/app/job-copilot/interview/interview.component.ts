import { Component, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language';
import { TranslateModule } from '@ngx-translate/core';
import { JobCopilotService } from '../shared/job-copilot.service';
import { JobOffer } from '../shared/job-copilot.model';
import { InterviewVoiceRecorder } from './interview-voice-recorder';
import { COVER_LETTER_LANGUAGES } from '../shared/apply-modal/components/apply-cover/apply-cover.component';
import { ConversationService } from '../../admin/conversation/service/conversation.service';
import { IConversation } from '../../admin/conversation/conversation.model';
import { Conversation } from '../../admin/conversation/list/conversation';

interface InterviewChatMessage {
  role: 'recruiter' | 'candidate';
  text: string;
  /** True when this turn was spoken rather than typed, so the UI can mark it. */
  spoken?: boolean;
}

@Component({
  selector: 'jhi-job-copilot-interview',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, FaIconComponent, TranslateDirective, TranslateModule, Conversation],
  templateUrl: './interview.component.html',
  styleUrl: './interview.component.scss',
})
export class InterviewComponent implements OnInit, OnDestroy {
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

  /** Voice mode: the backend must have the models wired AND the browser must be able to record. */
  readonly voiceSupported = InterviewVoiceRecorder.isSupported();
  voiceAvailable = signal(false);
  recording = signal(false);
  /** True while the recruiter's reply is being synthesised or played. */
  speaking = signal(false);

  private recorder: InterviewVoiceRecorder | null = null;
  /** Object URL of the audio currently loaded, revoked before the next one replaces it. */
  private audioUrl: string | null = null;
  private player: HTMLAudioElement | null = null;

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

    if (this.voiceSupported) {
      this.service.isInterviewVoiceAvailable().subscribe({
        next: available => this.voiceAvailable.set(available),
        // A probe failure just means no voice mode; the typed interview is unaffected.
        error: () => this.voiceAvailable.set(false),
      });
    }
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

  /** Starts a session whose first question is spoken. */
  startVoice(): void {
    const jobOfferId = this.selectedJobOfferId();
    if (!jobOfferId || this.loading()) return;
    this.loading.set(true);
    this.error.set(null);
    this.messages.set([]);
    this.conversationId.set(null);

    this.service.startInterviewVoice(jobOfferId, this.language() || undefined).subscribe({
      next: turn => {
        this.conversationId.set(turn.conversationId);
        this.messages.set(turn.reply ? [{ role: 'recruiter', text: turn.reply, spoken: true }] : []);
        this.loading.set(false);
        if (turn.reply) {
          this.createNewConversation(turn.conversationId, turn.reply);
        }
        this.play(turn.audioBase64, turn.audioFormat);
      },
      error: err => {
        this.error.set(err.error?.message ?? 'Failed to start the spoken interview');
        this.loading.set(false);
      },
    });
  }

  /** Push-to-talk: first click records, second click sends. */
  toggleRecording(): void {
    if (this.recording()) {
      void this.stopAndSend();
    } else {
      void this.startRecording();
    }
  }

  /** Drops the current recording without sending it. */
  cancelRecording(): void {
    this.recorder?.cancel();
    this.recorder = null;
    this.recording.set(false);
  }

  private async startRecording(): Promise<void> {
    if (!this.conversationId() || this.sending()) return;
    this.stopPlayback();
    this.error.set(null);
    this.recorder = new InterviewVoiceRecorder();
    try {
      await this.recorder.start();
      this.recording.set(true);
    } catch {
      // Denied permission, or no input device: both are the user's to fix, not errors to retry.
      this.recorder = null;
      this.error.set('Microphone unavailable — check the browser permission.');
    }
  }

  private async stopAndSend(): Promise<void> {
    const recorded = await this.recorder?.stop();
    this.recorder = null;
    this.recording.set(false);

    const conversationId = this.conversationId();
    const jobOfferId = this.selectedJobOfferId();
    if (!recorded || !conversationId || !jobOfferId) return;

    this.sending.set(true);
    this.service.sendInterviewVoiceAnswer(conversationId, jobOfferId, recorded, this.language() || undefined).subscribe({
      next: turn => {
        this.sending.set(false);
        if (!turn.reply) {
          // Nothing was heard: say so instead of appending an empty exchange.
          this.error.set('Nothing was picked up — try recording again.');
          return;
        }
        this.messages.update(msgs => [
          ...msgs,
          { role: 'candidate', text: turn.transcript, spoken: true },
          { role: 'recruiter', text: turn.reply!, spoken: true },
        ]);
        this.play(turn.audioBase64, turn.audioFormat);
      },
      error: err => {
        this.error.set(err.error?.message ?? 'Failed to send your recording');
        this.sending.set(false);
      },
    });
  }

  /** Plays the recruiter's reply, replacing whatever was playing before. */
  private play(audioBase64: string | null, format: string | null): void {
    if (!audioBase64) return;
    this.stopPlayback();
    const bytes = Uint8Array.from(atob(audioBase64), c => c.charCodeAt(0));
    const blob = new Blob([bytes], { type: `audio/${format ?? 'mpeg'}` });
    this.audioUrl = URL.createObjectURL(blob);
    this.player = new Audio(this.audioUrl);
    this.speaking.set(true);
    this.player.onended = () => this.speaking.set(false);
    // Autoplay can be blocked until the page has been interacted with; the turn already required a click,
    // but a rejection must still not leave the UI stuck on "speaking".
    this.player.play().catch(() => this.speaking.set(false));
  }

  private stopPlayback(): void {
    this.player?.pause();
    this.player = null;
    this.speaking.set(false);
    if (this.audioUrl) {
      URL.revokeObjectURL(this.audioUrl);
      this.audioUrl = null;
    }
  }

  reset(): void {
    this.cancelRecording();
    this.stopPlayback();
    this.conversationId.set(null);
    this.messages.set([]);
    this.answer.set('');
    this.error.set(null);
  }

  /** Leaving the page must release the mic and the audio URL, not wait for the GC. */
  ngOnDestroy(): void {
    this.cancelRecording();
    this.stopPlayback();
  }

  selectedOffer(): JobOffer | undefined {
    return this.jobOffers().find(o => o.id === this.selectedJobOfferId());
  }

  trackByIndex(index: number): number {
    return index;
  }
}
