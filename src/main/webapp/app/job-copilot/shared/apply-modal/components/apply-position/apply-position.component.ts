import { ChangeDetectionStrategy, Component, computed, inject, input, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { JobCopilotService } from '../../job-copilot.service';
import { CvMatchResult } from '../../job-copilot.model';
import { MatchResultComponent } from '../../match-result/match-result.component';
import TranslateDirective from '../../../../shared/language/translate.directive';

@Component({
  selector: 'jhi-apply-position',
  standalone: true,
  imports: [CommonModule, FaIconComponent, TranslatePipe, TranslateDirective, MatchResultComponent],
  templateUrl: './apply-position.component.html',
  styleUrl: './apply-position.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ApplyPositionComponent implements OnInit {
  offerId = input.required<string | undefined>();

  positioning = signal<CvMatchResult | null>(null);
  positioningLoading = signal(false);
  positioningError = signal<string | null>(null);
  selectedAdditions = signal<string[]>([]);
  simulated = signal<CvMatchResult | null>(null);
  simulating = signal(false);

  simulationDelta = computed(() => {
    const base = this.positioning();
    const sim = this.simulated();
    return base && sim ? sim.globalScore - base.globalScore : 0;
  });

  private service = inject(JobCopilotService);
  private translateService = inject(TranslateService);

  ngOnInit(): void {
    this.loadPositioning();
  }

  loadPositioning(): void {
    const id = this.offerId();
    if (!id) return;
    this.positioningLoading.set(true);
    this.positioningError.set(null);
    this.service.getPositioning(id).subscribe({
      next: result => {
        this.positioning.set(result);
        this.positioningLoading.set(false);
      },
      error: err => {
        this.positioningError.set(err.error?.message ?? this.translateService.instant('jobCopilot.positioning.error'));
        this.positioningLoading.set(false);
      },
    });
  }

  toggleAddition(skill: string): void {
    this.selectedAdditions.update(list => (list.includes(skill) ? list.filter(s => s !== skill) : [...list, skill]));
  }

  isSelected(skill: string): boolean {
    return this.selectedAdditions().includes(skill);
  }

  simulate(): void {
    const id = this.offerId();
    if (!id || this.simulating() || this.selectedAdditions().length === 0) return;
    this.simulating.set(true);
    this.service.simulatePositioning(id, this.selectedAdditions()).subscribe({
      next: result => {
        this.simulated.set(result);
        this.simulating.set(false);
      },
      error: () => this.simulating.set(false),
    });
  }
}
