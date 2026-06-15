import { Component, computed, inject, input, OnInit, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SafeHtml } from '@angular/platform-browser';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateService } from '@ngx-translate/core';
import { marked } from 'marked';

import { JobCopilotService } from '../job-copilot.service';
import { SanitizerService } from '../../../shared/sanitizer/sanitizer.service';
import { JobApplication, JobOfferPreview, jobSourceColor, jobSourceLabel } from '../job-copilot.model';
import { TranslatePipe } from "@ngx-translate/core";
import TranslateDirective from "../../../shared/language/translate.directive";

/** Strips a leading/trailing ```` ```markdown ... ``` ```` (or ```` ``` ... ``` ````) fence some LLMs wrap their output in. */
function stripMarkdownFence(text: string): string {
  const trimmed = text.trim();
  const match = /^```[a-zA-Z]*\n([\s\S]*)\n```$/.exec(trimmed);
  return match ? match[1].trim() : trimmed;
}

/** Languages the cover letter can be generated in. The value is sent to the LLM as part of its prompt. */
export const COVER_LETTER_LANGUAGES: { value: string; label: string; labelKey: string }[] = [
  { value: '', label: "Langue de l'offre", labelKey: 'jobCopilot.applyModal.languages.offer' },
  { value: 'French', label: 'Français', labelKey: 'jobCopilot.applyModal.languages.french' },
  { value: 'English', label: 'English', labelKey: 'jobCopilot.applyModal.languages.english' },
  { value: 'Spanish', label: 'Español', labelKey: 'jobCopilot.applyModal.languages.spanish' },
  { value: 'German', label: 'Deutsch', labelKey: 'jobCopilot.applyModal.languages.german' },
];

@Component({
  selector: 'jhi-apply-modal',
  standalone: true,
  imports: [CommonModule, FormsModule, FaIconComponent, TranslatePipe, TranslateDirective],
  templateUrl: './apply-modal.component.html',
  styleUrl: './apply-modal.component.scss',
})
export class ApplyModalComponent implements OnInit {
  /** Provide either an existing draft application... */
  existingApplication = input<JobApplication | undefined>();
  /** ...or a job offer to create a new draft application for. */
  jobOffer = input<JobOfferPreview | undefined>();
  jobOfferId = input<string | undefined>();
  closed = output<void>();
  applied = output<JobApplication>();
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
  private translateService = inject(TranslateService);

  ngOnInit(): void {
    this.service.getAutoApplySources().subscribe({
      next: sources => this.autoApplySupported.set(sources.includes(this.offer()?.source ?? '')),
      error() {},
    });

    if (this.existingApplication()) {
      this.application.set(this.existingApplication()!);
      this.coverLetter.set(this.existingApplication()?.coverLetter ?? '');
      this.loading.set(false);
    } else if (this.jobOfferId()) {
      this.service.createApplication(this.jobOfferId()!, true, this.language() || undefined).subscribe({
        next: app => {
          this.application.set(app);
          this.coverLetter.set(app.coverLetter ?? '');
          this.loading.set(false);
        },
        error: err => {
          this.error.set(err.error?.message ?? this.translateService.instant('jobCopilot.applyModal.error.create'));
          this.loading.set(false);
        },
      });
    } else {
      this.loading.set(false);
      this.error.set(this.translateService.instant('jobCopilot.applyModal.error.noOffer'));
    }
  }

  offer = computed(() => this.application()?.jobOffer ?? this.jobOffer());

  sourceLabel = computed(() => jobSourceLabel(this.offer()?.source));

  sourceColor = computed(() => jobSourceColor(this.offer()?.source));

  applyUrl = computed(() => this.offer()?.applyUrl);

  togglePreview(): void {
    this.previewMode.update(v => !v);
  }

  offerId = computed(() => this.jobOfferId() ?? this.application()?.jobOffer?.id);

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
        this.error.set(err.error?.message ?? this.translateService.instant('jobCopilot.applyModal.error.regenerate'));
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
              this.resultMessage.set(this.translateService.instant('jobCopilot.applyModal.success.autoApplied', { source: this.sourceLabel() }));
              this.applied.emit(outcome.application);
            } else {
              this.error.set(outcome.message);
            }
          },
          error: err => {
            this.submitting.set(false);
            this.error.set(err.error?.message ?? this.translateService.instant('jobCopilot.applyModal.error.autoApply'));
          },
        });
      },
      error: err => {
        this.submitting.set(false);
        this.error.set(err.error?.message ?? this.translateService.instant('jobCopilot.applyModal.error.save'));
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
            this.resultMessage.set(this.translateService.instant('jobCopilot.applyModal.success.markedApplied'));
            this.applied.emit(updated);
          },
          error: err => {
            this.submitting.set(false);
            this.error.set(err.error?.message ?? this.translateService.instant('jobCopilot.applyModal.error.status'));
          },
        });
      },
      error: err => {
        this.submitting.set(false);
        this.error.set(err.error?.message ?? this.translateService.instant('jobCopilot.applyModal.error.save'));
      },
    });
  }

  close(): void {
    this.closed.emit();
  }
}
