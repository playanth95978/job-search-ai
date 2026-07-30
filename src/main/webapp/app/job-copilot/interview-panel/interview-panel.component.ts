import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateModule } from '@ngx-translate/core';

import { TranslateDirective } from 'app/shared/language';
import { JobCopilotService } from '../shared/job-copilot.service';
import { JobOffer, PanelReport } from '../shared/job-copilot.model';
import { COVER_LETTER_LANGUAGES } from '../shared/apply-modal/apply-modal.component';

interface PanelTurn {
  side: 'candidate' | 'juror';
  role?: string;
  speaker?: string;
  text: string;
}

interface JurorMeta {
  icon: string;
  color: string;
}

const JUROR_META: Record<string, JurorMeta> = {
  HR: { icon: 'handshake', color: '#8b5cf6' },
  TECH_LEAD: { icon: 'gear', color: '#3b82f6' },
  MANAGER: { icon: 'chart-line', color: '#10b981' },
};

/**
 * "Shadow Interview Panel" UI: an interview simulated by a 3-agent jury (HR, Tech Lead, Manager).
 * Each juror's turn is labelled with its avatar/colour; ending the interview shows the cross
 * deliberation (per-juror verdicts + synthesis).
 */
@Component({
  selector: 'jhi-interview-panel',
  standalone: true,
  imports: [CommonModule, FormsModule, FaIconComponent, TranslateDirective, TranslateModule],
  templateUrl: './interview-panel.component.html',
  styleUrl: './interview-panel.component.scss',
})
export class InterviewPanelComponent implements OnInit {
  private readonly service = inject(JobCopilotService);

  readonly jobOffers = signal<JobOffer[]>([]);
  readonly selectedJobOfferId = signal('');
  readonly conversationId = signal<string | null>(null);
  readonly turns = signal<PanelTurn[]>([]);
  readonly report = signal<PanelReport | null>(null);
  answer = '';
  readonly loading = signal(false);
  readonly sending = signal(false);
  readonly finishing = signal(false);
  readonly error = signal<string | null>(null);
  language = '';

  readonly languages = COVER_LETTER_LANGUAGES;
  readonly started = computed(() => this.conversationId() !== null);

  ngOnInit(): void {
    this.service.getApplications().subscribe({
      next: apps => {
        const offers = apps.map(a => a.jobOffer).filter((o): o is JobOffer => !!o?.id);
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
    this.report.set(null);
    this.turns.set([]);
    this.conversationId.set(null);
    this.service.startPanel(jobOfferId, this.language || undefined).subscribe({
      next: res => {
        this.conversationId.set(res.conversationId);
        this.turns.set([{ side: 'juror', role: res.role, speaker: res.speaker, text: res.message }]);
        this.loading.set(false);
      },
      error: err => {
        this.error.set(err.error?.message ?? 'Failed to start the panel');
        this.loading.set(false);
      },
    });
  }

  send(): void {
    const text = this.answer.trim();
    const conversationId = this.conversationId();
    const jobOfferId = this.selectedJobOfferId();
    if (!text || !conversationId || !jobOfferId || this.sending()) return;

    this.turns.update(t => [...t, { side: 'candidate', text }]);
    this.answer = '';
    this.sending.set(true);
    this.error.set(null);
    this.service.sendPanelAnswer(conversationId, jobOfferId, text, this.language || undefined).subscribe({
      next: res => {
        this.turns.update(t => [...t, { side: 'juror', role: res.role, speaker: res.speaker, text: res.message }]);
        this.sending.set(false);
      },
      error: err => {
        this.error.set(err.error?.message ?? 'Failed to send your answer');
        this.sending.set(false);
      },
    });
  }

  finish(): void {
    const conversationId = this.conversationId();
    const jobOfferId = this.selectedJobOfferId();
    if (!conversationId || !jobOfferId || this.finishing()) return;
    this.finishing.set(true);
    this.error.set(null);
    this.service.getPanelReport(conversationId, jobOfferId, this.language || undefined).subscribe({
      next: r => {
        this.report.set(r);
        this.finishing.set(false);
      },
      error: err => {
        this.error.set(err.error?.message ?? 'Failed to load the report');
        this.finishing.set(false);
      },
    });
  }

  reset(): void {
    this.conversationId.set(null);
    this.turns.set([]);
    this.report.set(null);
    this.answer = '';
    this.error.set(null);
  }

  jurorIcon(role?: string): string {
    return JUROR_META[role ?? '']?.icon ?? 'user-tie';
  }

  jurorColor(role?: string): string {
    return JUROR_META[role ?? '']?.color ?? '#4f46e5';
  }

  recommendationClass(recommendation: string): string {
    switch (recommendation) {
      case 'STRONG_HIRE':
        return 'reco-strong';
      case 'HIRE':
        return 'reco-hire';
      case 'NO_HIRE':
        return 'reco-no';
      default:
        return 'reco-neutral';
    }
  }

  selectedOffer(): JobOffer | undefined {
    return this.jobOffers().find(o => o.id === this.selectedJobOfferId());
  }
}
