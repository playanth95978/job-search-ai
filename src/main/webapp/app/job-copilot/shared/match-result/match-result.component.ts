import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateModule } from '@ngx-translate/core';
import TranslateDirective from 'app/shared/language/translate.directive';

import { CvMatchResult } from '../job-copilot.model';

type Rating = 'good' | 'fair' | 'poor';

/**
 * Presentational rendering of a {@link CvMatchResult}: global score + sub-scores, missing
 * skills/keywords, strengths, weaknesses and suggestions. Reused by the CV Builder match panel and
 * the offer positioning section so the markup/CSS lives in one place.
 */
@Component({
  selector: 'jhi-match-result',
  standalone: true,
  imports: [CommonModule, FaIconComponent, TranslateModule, TranslateDirective],
  templateUrl: './match-result.component.html',
  styleUrl: './match-result.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MatchResultComponent {
  readonly result = input.required<CvMatchResult>();

  readonly globalRating = computed<Rating>(() => MatchResultComponent.rate(this.result().globalScore));

  static rate(score: number): Rating {
    return score >= 80 ? 'good' : score >= 50 ? 'fair' : 'poor';
  }
}
