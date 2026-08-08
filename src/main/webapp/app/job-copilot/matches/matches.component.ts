import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateModule } from '@ngx-translate/core';

import { TranslateDirective } from 'app/shared/language';
import { JobCopilotService } from '../shared/job-copilot.service';
import { JobOfferDto, MatchResult } from '../shared/job-copilot.model';

/**
 * "All matches" page: the full set of offers scored by the advanced matching pipeline
 * (hybrid vector + BM25 + RRF + cross-encoder reranking) against the signed-in user's profile.
 * Signal-first and self-contained.
 */
@Component({
  selector: 'jhi-job-copilot-matches',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe, RouterLink, FaIconComponent, TranslateModule, TranslateDirective],
  templateUrl: './matches.component.html',
  styleUrl: './matches.component.scss',
})
export class MatchesComponent {
  private readonly service = inject(JobCopilotService);
  private readonly router = inject(Router);

  readonly matches = signal<MatchResult[]>([]);
  readonly loading = signal(false);
  readonly loaded = signal(false);
  readonly error = signal<string | null>(null);
  /** Id of the offer currently being turned into an application (disables its button). */
  readonly applyingId = signal<string | null>(null);

  constructor() {
    this.load();
  }

  load(): void {
    if (this.loading()) return;
    this.loading.set(true);
    this.error.set(null);
    this.service.matchAdvanced().subscribe({
      next: results => {
        this.matches.set(results);
        this.loading.set(false);
        this.loaded.set(true);
      },
      error: err => {
        this.error.set(err.error?.message ?? 'Failed to load matches');
        this.loading.set(false);
        this.loaded.set(true);
      },
    });
  }

  scoreClass(score: number): string {
    if (score >= 0.7) return 'high';
    if (score >= 0.4) return 'medium';
    return 'low';
  }

  apply(offer: JobOfferDto): void {
    if (!offer.id || this.applyingId()) return;
    this.applyingId.set(offer.id);
    this.service.createApplication(offer.id).subscribe({
      next: () => {
        this.applyingId.set(null);
        this.router.navigate(['/job-copilot/applications']);
      },
      error: () => this.applyingId.set(null),
    });
  }
}
