import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateModule } from '@ngx-translate/core';
import { TranslateDirective } from 'app/shared/language';

import { CareerRoadmap, JobOfferDto, RoadmapStep, RoadmapStepType } from '../shared/job-copilot.model';
import { JobCopilotService } from '../shared/job-copilot.service';

/**
 * Predictive career-path simulator page: the user names a target senior role and a 2/5-year horizon;
 * the page shows a deterministic, offer-grounded skill gap plus an AI learning roadmap.
 */
@Component({
  selector: 'jhi-career-path',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, FaIconComponent, TranslateModule, TranslateDirective],
  templateUrl: './career-path.component.html',
  styleUrl: './career-path.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CareerPathComponent {
  private service = inject(JobCopilotService);

  readonly targetRole = signal('');
  readonly horizon = signal<2 | 5>(5);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly roadmap = signal<CareerRoadmap | null>(null);

  readonly missingSkills = computed(() => (this.roadmap()?.skillGaps ?? []).filter(g => !g.owned));
  readonly ownedSkills = computed(() => (this.roadmap()?.skillGaps ?? []).filter(g => g.owned));

  readonly canSubmit = computed(() => this.targetRole().trim().length > 1 && !this.loading());

  runSimulation(): void {
    const role = this.targetRole().trim();
    if (role.length < 2) return;
    this.loading.set(true);
    this.error.set(null);
    this.service.simulateCareer(role, this.horizon()).subscribe({
      next: r => {
        this.roadmap.set(r);
        this.loading.set(false);
      },
      error: err => {
        this.error.set(err.error?.message || 'Career simulation failed');
        this.loading.set(false);
      },
    });
  }

  readinessColor(score: number): string {
    if (score >= 66) return 'success';
    if (score >= 33) return 'warning';
    return 'danger';
  }

  stepIcon(type: RoadmapStepType): string {
    switch (type) {
      case 'ROLE':
        return 'user-tie';
      case 'CERTIFICATION':
        return 'certificate';
      case 'PROJECT':
        return 'diagram-project';
      default:
        return 'graduation-cap';
    }
  }

  /** Resolves a step's evidence offer ids to the sample offers we can actually display. */
  offersForStep(step: RoadmapStep): JobOfferDto[] {
    const sample = this.roadmap()?.sampleOffers ?? [];
    return sample.filter(o => o.id && step.evidenceOfferIds.includes(o.id));
  }
}
