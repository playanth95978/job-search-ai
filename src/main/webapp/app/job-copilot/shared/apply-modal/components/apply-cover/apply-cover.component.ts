import { ChangeDetectionStrategy, Component, computed, inject, input, model, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SafeHtml } from '@angular/platform-browser';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { marked } from 'marked';
import { JobCopilotService } from '../../job-copilot.service';
import { SanitizerService } from '../../../../shared/sanitizer/sanitizer.service';
import { JobApplication } from '../../job-copilot.model';
import TranslateDirective from '../../../../shared/language/translate.directive';

/** Strips a leading/trailing ```` ```markdown ... ``` ```` (or ```` ``` ... ``` ````) fence some LLMs wrap their output in. */
function stripMarkdownFence(text: string): string {
  const trimmed = text.trim();
  const match = /^```[a-zA-Z]*\n([\s\S]*)\n```$/.exec(trimmed);
  return match ? match[1].trim() : trimmed;
}

export const COVER_LETTER_LANGUAGES: { value: string; label: string; labelKey: string }[] = [
  { value: '', label: "Langue de l'offre", labelKey: 'jobCopilot.applyModal.languages.offer' },
  { value: 'French', label: 'Français', labelKey: 'jobCopilot.applyModal.languages.french' },
  { value: 'English', label: 'English', labelKey: 'jobCopilot.applyModal.languages.english' },
  { value: 'Spanish', label: 'Español', labelKey: 'jobCopilot.applyModal.languages.spanish' },
  { value: 'German', label: 'Deutsch', labelKey: 'jobCopilot.applyModal.languages.german' },
];

@Component({
  selector: 'jhi-apply-cover',
  standalone: true,
  imports: [CommonModule, FormsModule, FaIconComponent, TranslatePipe, TranslateDirective],
  templateUrl: './apply-cover.component.html',
  styleUrl: './apply-cover.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ApplyCoverComponent {
  application = input<JobApplication | null>(null);
  offerId = input<string | undefined>();
  loading = input(false);
  coverLetter = model('');
  language = signal('');

  previewMode = signal(true);
  regenerating = signal(false);
  error = signal<string | null>(null);

  readonly languages = COVER_LETTER_LANGUAGES;

  private service = inject(JobCopilotService);
  private sanitizer = inject(SanitizerService);
  private translateService = inject(TranslateService);

  coverLetterHtml = computed<SafeHtml>(() =>
    this.sanitizer.sanitizeHtml(marked.parse(stripMarkdownFence(this.coverLetter()), { async: false })),
  );

  togglePreview(): void {
    this.previewMode.update(v => !v);
  }

  regenerateCoverLetter(): void {
    const id = this.offerId();
    if (!id) return;
    this.regenerating.set(true);
    this.error.set(null);
    this.service.generateCoverLetter(id, this.language() || undefined).subscribe({
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
}
