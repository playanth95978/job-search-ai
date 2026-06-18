import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateModule } from '@ngx-translate/core';
import TranslateDirective from 'app/shared/language/translate.directive';

import { Resume, resumeToPlainText } from '../resume.model';
import { CvMatchResult, CvResumeApiService } from '../cv-resume-api.service';

type Rating = 'good' | 'fair' | 'poor';

/** Match the current resume against a pasted job offer: global + sub-scores, gaps, suggestions. */
@Component({
  selector: 'jhi-cv-match',
  imports: [CommonModule, FormsModule, FaIconComponent, TranslateModule, TranslateDirective],
  templateUrl: './cv-match.component.html',
  styleUrl: './cv-match.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CvMatchComponent {
  private readonly api = inject(CvResumeApiService);

  readonly resume = input.required<Resume>();

  readonly open = signal(false);
  readonly offer = signal('');
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly result = signal<CvMatchResult | null>(null);

  readonly globalRating = computed<Rating>(() => this.rate(this.result()?.globalScore ?? 0));

  rate(score: number): Rating {
    return score >= 80 ? 'good' : score >= 50 ? 'fair' : 'poor';
  }

  run(): void {
    const offer = this.offer().trim();
    if (!offer || this.loading()) return;
    this.loading.set(true);
    this.error.set(null);
    this.api.matchResume(resumeToPlainText(this.resume()), offer).subscribe({
      next: r => {
        this.result.set(r);
        this.loading.set(false);
      },
      error: err => {
        this.error.set(err.error?.message ?? 'AI match failed');
        this.loading.set(false);
      },
    });
  }
}
