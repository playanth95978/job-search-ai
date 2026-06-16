import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateModule } from '@ngx-translate/core';
import TranslateDirective from 'app/shared/language/translate.directive';

import { CvReview } from '../../shared/job-copilot.model';
import { Resume, resumeToPlainText } from '../resume.model';
import { CvResumeApiService } from '../cv-resume-api.service';

/** AI review of the whole resume: a score plus strengths and actionable improvements. */
@Component({
  selector: 'jhi-cv-review',
  standalone: true,
  imports: [CommonModule, FaIconComponent, TranslateModule, TranslateDirective],
  templateUrl: './cv-review.component.html',
  styleUrl: './cv-review.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CvReviewComponent {
  private readonly api = inject(CvResumeApiService);

  readonly resume = input.required<Resume>();

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly review = signal<CvReview | null>(null);

  readonly scoreClass = computed(() => {
    const r = this.review();
    if (!r) return '';
    return r.score >= 80 ? 'good' : r.score >= 50 ? 'fair' : 'poor';
  });

  run(): void {
    if (this.loading()) return;
    this.loading.set(true);
    this.error.set(null);
    this.api.reviewResume(resumeToPlainText(this.resume())).subscribe({
      next: r => {
        this.review.set(r);
        this.loading.set(false);
      },
      error: err => {
        this.error.set(err.error?.message ?? 'AI review failed');
        this.loading.set(false);
      },
    });
  }
}
