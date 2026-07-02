import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TranslateDirective } from 'app/shared/language';

import { CandidateProfile, CvReview, AtsSimulation, AtsWarning } from '../job-copilot.model';
import { JobCopilotService } from '../job-copilot.service';
import { analyzeCv, CvMetric, CvRating, overallRating, overallScore, ratingLabel } from '../cv-quality';
import { analyzeSearchability, AtsCheck, AtsCheckCategory, parseRateRating } from '../ats-searchability';

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

  readonly activeTab = signal<'score' | 'ats'>('score');

  readonly atsSimulation = signal<AtsSimulation | null>(null);
  readonly simulating = signal(false);
  readonly simulationError = signal<string | null>(null);

  // Deterministic Jobscan-style searchability report, computed locally from the profile.
  readonly searchability = computed(() => analyzeSearchability(this.profile()));
  readonly parseRateRating = computed<CvRating>(() => parseRateRating(this.searchability().parseRate));
  readonly showRawText = signal(false);
  readonly showDeepScan = signal(false);

  private readonly categoryOrder: AtsCheckCategory[] = ['contact', 'sections', 'formatting'];

  readonly checkGroups = computed<{ category: AtsCheckCategory; checks: AtsCheck[] }[]>(() => {
    const checks = this.searchability().checks;
    return this.categoryOrder
      .map(category => ({ category, checks: checks.filter(c => c.category === category) }))
      .filter(group => group.checks.length > 0);
  });

  readonly ratingLabel = ratingLabel;

  iconFor(rating: CvRating): string {
    if (rating === 'good') return 'circle-check';
    if (rating === 'fair') return 'circle-exclamation';
    return 'triangle-exclamation';
  }

  checkIcon(status: AtsCheck['status']): string {
    if (status === 'pass') return 'circle-check';
    if (status === 'warn') return 'circle-exclamation';
    return 'circle-xmark';
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

  runAtsSimulation(): void {
    this.simulating.set(true);
    this.simulationError.set(null);
    this.service.simulateAtsParsing().subscribe({
      next: sim => {
        this.atsSimulation.set(sim);
        this.simulating.set(false);
      },
      error: err => {
        this.simulationError.set(err.error?.message || 'ATS simulation failed');
        this.simulating.set(false);
      },
    });
  }
}
