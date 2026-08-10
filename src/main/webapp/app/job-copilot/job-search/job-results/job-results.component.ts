import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateModule } from '@ngx-translate/core';

import TranslateDirective from '../../../shared/language/translate.directive';
import { JobOffer, jobSourceColor, jobSourceLabel, MatchResult } from '../../shared/job-copilot.model';
import { OfferDetail } from '../job-search.types';

/**
 * Results column of the job-search page: renders either the profile-match cards or the plain offer
 * cards. Presentational and signal-based (inputs/outputs only) — selection and apply are delegated
 * to the parent, which owns the data and the detail pane.
 */
@Component({
  selector: 'jhi-job-results',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe, FaIconComponent, TranslateModule, TranslateDirective],
  templateUrl: './job-results.component.html',
  styleUrl: './job-results.component.scss',
})
export class JobResultsComponent {
  readonly offers = input.required<JobOffer[]>();
  readonly matchResults = input.required<MatchResult[]>();
  readonly selectedId = input<string | null>(null);

  readonly selectOffer = output<OfferDetail>();
  readonly apply = output<OfferDetail>();

  getScoreClass(score: number): string {
    if (score >= 0.7) return 'high';
    if (score >= 0.4) return 'medium';
    return 'low';
  }

  getSourceLabel(source?: string): string {
    return jobSourceLabel(source);
  }

  getSourceColor(source?: string): string {
    return jobSourceColor(source);
  }
}
