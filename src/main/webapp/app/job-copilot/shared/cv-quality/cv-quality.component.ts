import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TranslateDirective } from 'app/shared/language';

import { CandidateProfile, CvReview } from '../job-copilot.model';
import { JobCopilotService } from '../job-copilot.service';
import { analyzeCv, CvMetric, CvRating, overallRating, overallScore, ratingLabel } from '../cv-quality';

@Component({
  selector: 'jhi-cv-quality',
  standalone: true,
  imports: [CommonModule, FaIconComponent, TranslateModule, TranslateDirective],
  templateUrl: './cv-quality.component.html',
  styleUrl: './cv-quality.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CvQualityComponent {
  private service = inject(JobCopilotService);
  private translateService = inject(TranslateService);

  readonly profile = input.required<CandidateProfile>();

  readonly filename = computed(() => this.profile().cvFilename);
  readonly metrics = computed<CvMetric[]>(() => analyzeCv(this.profile()));
  readonly score = computed(() => overallScore(this.metrics()));
  readonly scoreRating = computed<CvRating>(() => overallRating(this.score()));

  readonly review = signal<CvReview | null>(null);
  readonly reviewing = signal(false);
  readonly reviewError = signal<string | null>(null);

  readonly ratingLabel = ratingLabel;

  iconFor(rating: CvRating): string {
    if (rating === 'good') return 'circle-check';
    if (rating === 'fair') return 'circle-exclamation';
    return 'triangle-exclamation';
  }

  runAiReview(): void {
    this.reviewing.set(true);
    this.reviewError.set(null);
    this.service.reviewCv().subscribe({
      next: r => {
        this.review.set(r);
        this.reviewing.set(false);
      },
      error: err => {
        this.reviewError.set(err.error?.message || 'AI review failed');
        this.reviewing.set(false);
      },
    });
  }
}
