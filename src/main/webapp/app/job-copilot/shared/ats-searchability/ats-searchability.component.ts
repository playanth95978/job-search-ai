import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateModule } from '@ngx-translate/core';
import { TranslateDirective } from 'app/shared/language';

import { CandidateProfile, AtsSimulation } from '../job-copilot.model';
import { JobCopilotService } from '../job-copilot.service';
import { analyzeSearchability, AtsCheck, AtsCheckCategory, parseRateRating } from './ats-searchability';

/**
 * Deterministic, Jobscan-style ATS "searchability" report for the ingested CV.
 * The main report is computed locally from the profile; an optional AI deep-scan
 * (backed by /cv/ats-simulation) complements it for layout issues the checks can't see.
 */
@Component({
  selector: 'jhi-ats-searchability',
  standalone: true,
  imports: [CommonModule, FaIconComponent, TranslateModule, TranslateDirective],
  templateUrl: './ats-searchability.component.html',
  styleUrl: './ats-searchability.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AtsSearchabilityComponent {
  private service = inject(JobCopilotService);

  readonly profile = input.required<CandidateProfile>();

  readonly searchability = computed(() => analyzeSearchability(this.profile()));
  readonly parseRateRating = computed(() => parseRateRating(this.searchability().parseRate));

  readonly showRawText = signal(false);
  readonly showDeepScan = signal(false);

  readonly atsSimulation = signal<AtsSimulation | null>(null);
  readonly simulating = signal(false);
  readonly simulationError = signal<string | null>(null);

  private readonly categoryOrder: AtsCheckCategory[] = ['contact', 'sections', 'formatting'];

  readonly checkGroups = computed<{ category: AtsCheckCategory; checks: AtsCheck[] }[]>(() => {
    const checks = this.searchability().checks;
    return this.categoryOrder
      .map(category => ({ category, checks: checks.filter(c => c.category === category) }))
      .filter(group => group.checks.length > 0);
  });

  checkIcon(status: AtsCheck['status']): string {
    if (status === 'pass') return 'circle-check';
    if (status === 'warn') return 'circle-exclamation';
    return 'circle-xmark';
  }

  /** Maps a check status to a Bootstrap contextual color name. */
  statusColor(status: AtsCheck['status']): string {
    if (status === 'pass') return 'success';
    if (status === 'warn') return 'warning';
    return 'danger';
  }

  /** Maps the parse-rate rating to a Bootstrap contextual color name. */
  ratingColor(rating: 'good' | 'fair' | 'poor'): string {
    if (rating === 'good') return 'success';
    if (rating === 'fair') return 'warning';
    return 'danger';
  }

  /** Maps a deep-scan warning level to a Bootstrap contextual color name. */
  levelColor(level: string): string {
    const l = level.toLowerCase();
    if (l === 'critical') return 'danger';
    if (l === 'warning') return 'warning';
    return 'info';
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
