import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateModule } from '@ngx-translate/core';

import { JobCopilotService } from '../shared/job-copilot.service';
import { AutopsyReport } from '../shared/job-copilot.model';

@Component({
  selector: 'jhi-job-copilot-insights',
  imports: [CommonModule, FaIconComponent, TranslateModule],
  templateUrl: './insights.component.html',
  styleUrl: './insights.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InsightsComponent implements OnInit {
  report = signal<AutopsyReport | null>(null);
  loading = signal(true);
  error = signal<string | null>(null);

  private service = inject(JobCopilotService);

  /** Funnel bar widths (%) relative to the number of applications, for the visualization. */
  bars = computed(() => {
    const f = this.report()?.funnel;
    if (!f || f.applied === 0) return null;
    return {
      applied: 100,
      interview: Math.round((f.interview / f.applied) * 100),
      offer: Math.round((f.offer / f.applied) * 100),
    };
  });

  ngOnInit(): void {
    this.service.getAutopsy().subscribe({
      next: r => {
        this.report.set(r);
        this.loading.set(false);
      },
      error: err => {
        this.error.set(err.error?.message ?? 'Failed to load insights');
        this.loading.set(false);
      },
    });
  }

  pct(value: number): number {
    return Math.round(value * 100);
  }
}
