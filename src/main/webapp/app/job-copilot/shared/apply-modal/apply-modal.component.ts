import { Component, computed, EventEmitter, inject, Input, OnInit, Output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SafeHtml } from '@angular/platform-browser';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { marked } from 'marked';

import { JobCopilotService } from '../job-copilot.service';
import { SanitizerService } from '../../../shared/sanitizer/sanitizer.service';
import { JobApplication, JobOfferPreview, jobSourceColor, jobSourceLabel } from '../job-copilot.model';

/** Strips a leading/trailing ```` ```markdown ... ``` ```` (or ```` ``` ... ``` ````) fence some LLMs wrap their output in. */
function stripMarkdownFence(text: string): string {
  const trimmed = text.trim();
  const match = /^```[a-zA-Z]*\n([\s\S]*)\n```$/.exec(trimmed);
  return match ? match[1].trim() : trimmed;
}

/** Languages the cover letter can be generated in. The value is sent to the LLM as part of its prompt. */
export const COVER_LETTER_LANGUAGES: { value: string; label: string }[] = [
  { value: '', label: "Langue de l'offre" },
  { value: 'French', label: 'Français' },
  { value: 'English', label: 'English' },
  { value: 'Spanish', label: 'Español' },
  { value: 'German', label: 'Deutsch' },
];

@Component({
  selector: 'jhi-apply-modal',
  standalone: true,
  imports: [CommonModule, FormsModule, FaIconComponent],
  templateUrl: './apply-modal.component.html',
  styleUrl: './apply-modal.component.scss',
})
export class ApplyModalComponent implements OnInit {
  /** Provide either an existing draft application... */
  @Input() existingApplication?: JobApplication;
  /** ...or a job offer to create a new draft application for. */
  @Input() jobOffer?: JobOfferPreview;
  @Input() jobOfferId?: string;
  @Output() closed = new EventEmitter<void>();
  @Output() applied = new EventEmitter<JobApplication>();
  application = signal<JobApplication | null>(null);
  coverLetter = signal('');
  loading = signal(true);
  submitting = signal(false);
  copied = signal(false);
  error = signal<string | null>(null);
  autoApplySupported = signal(false);
  resultMessage = signal<string | null>(null);
  previewMode = signal(true);
  language = signal('');
  regenerating = signal(false);
  readonly languages = COVER_LETTER_LANGUAGES;
  coverLetterHtml = computed<SafeHtml>(() =>
    this.sanitizer.sanitizeHtml(marked.parse(stripMarkdownFence(this.coverLetter()), { async: false })),
  );
  private service = inject(JobCopilotService);
  private sanitizer = inject(SanitizerService);

  ngOnInit(): void {
    this.service.getAutoApplySources().subscribe({
      next: sources => this.autoApplySupported.set(sources.includes(this.offer()?.source ?? '')),
      error() {},
    });

    if (this.existingApplication) {
      this.application.set(this.existingApplication);
      this.coverLetter.set(this.existingApplication.coverLetter ?? '');
      this.loading.set(false);
    } else if (this.jobOfferId) {
      this.service.createApplication(this.jobOfferId, true, this.language() || undefined).subscribe({
        next: app => {
          this.application.set(app);
          this.coverLetter.set(app.coverLetter ?? '');
          this.loading.set(false);
        },
        error: err => {
          this.error.set(err.error?.message ?? 'Failed to create application');
          this.loading.set(false);
        },
      });
    } else {
      this.loading.set(false);
      this.error.set('No job offer provided');
    }
  }

  offer(): JobOfferPreview | undefined {
    return this.application()?.jobOffer ?? this.jobOffer;
  }

  sourceLabel(): string {
    return jobSourceLabel(this.offer()?.source);
  }

  sourceColor(): string {
    return jobSourceColor(this.offer()?.source);
  }

  applyUrl(): string | undefined {
    return this.offer()?.applyUrl;
  }

  togglePreview(): void {
    this.previewMode.update(v => !v);
  }

  offerId(): string | undefined {
    return this.jobOfferId ?? this.application()?.jobOffer?.id;
  }

  regenerateCoverLetter(): void {
    const offerId = this.offerId();
    if (!offerId) return;
    this.regenerating.set(true);
    this.error.set(null);
    this.service.generateCoverLetter(offerId, this.language() || undefined).subscribe({
      next: letter => {
        this.coverLetter.set(letter);
        this.regenerating.set(false);
      },
      error: err => {
        this.error.set(err.error?.message ?? 'Failed to generate cover letter');
        this.regenerating.set(false);
      },
    });
  }

  copyToClipboard(): void {
    navigator.clipboard.writeText(this.coverLetter()).then(() => {
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 2000);
    });
  }

  submitAuto(): void {
    const app = this.application();
    if (!app?.id) return;
    this.submitting.set(true);
    this.error.set(null);
    this.service.updateCoverLetter(app.id, this.coverLetter()).subscribe({
      next: () => {
        this.service.applyToJob(app.id!).subscribe({
          next: outcome => {
            this.submitting.set(false);
            this.application.set(outcome.application);
            if (outcome.autoApplied) {
              this.resultMessage.set(`Candidature envoyée automatiquement via ${this.sourceLabel()}.`);
              this.applied.emit(outcome.application);
            } else {
              this.error.set(outcome.message);
            }
          },
          error: err => {
            this.submitting.set(false);
            this.error.set(err.error?.message ?? "L'envoi automatique a échoué");
          },
        });
      },
      error: err => {
        this.submitting.set(false);
        this.error.set(err.error?.message ?? 'Failed to save the cover letter');
      },
    });
  }

  markAsApplied(): void {
    const app = this.application();
    if (!app?.id) return;
    this.submitting.set(true);
    this.error.set(null);
    this.service.updateCoverLetter(app.id, this.coverLetter()).subscribe({
      next: () => {
        this.service.updateApplicationStatus(app.id!, 'APPLIED').subscribe({
          next: updated => {
            this.submitting.set(false);
            this.application.set(updated);
            this.resultMessage.set('Candidature marquée comme envoyée.');
            this.applied.emit(updated);
          },
          error: err => {
            this.submitting.set(false);
            this.error.set(err.error?.message ?? 'Failed to update status');
          },
        });
      },
      error: err => {
        this.submitting.set(false);
        this.error.set(err.error?.message ?? 'Failed to save the cover letter');
      },
    });
  }

  close(): void {
    this.closed.emit();
  }
}
