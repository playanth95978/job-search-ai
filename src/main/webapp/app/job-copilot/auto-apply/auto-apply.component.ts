import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateModule } from '@ngx-translate/core';

import { TranslateDirective } from 'app/shared/language';
import { JobCopilotService } from '../shared/job-copilot.service';
import { AutoApplyConfig, AutoApplyMode, AutoApplyRunResult } from '../shared/job-copilot.model';
import { ChipsInputComponent } from '../shared/chips-input/chips-input.component';

const DEFAULT_CONFIG: AutoApplyConfig = { mode: 'VALIDATION', minScore: 0.7, maxPerDay: 5, sources: [] };

/**
 * Auto-apply agent page: configure the Level-1 agent (mode + guardrails) and trigger a run on demand.
 * Signal-first; the config is the single source of truth, updated immutably.
 */
@Component({
  selector: 'jhi-job-copilot-auto-apply',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe, FormsModule, RouterLink, FaIconComponent, TranslateModule, TranslateDirective, ChipsInputComponent],
  templateUrl: './auto-apply.component.html',
  styleUrl: './auto-apply.component.scss',
})
export class AutoApplyComponent {
  readonly config = signal<AutoApplyConfig>(DEFAULT_CONFIG);
  readonly saving = signal(false);
  readonly savedOk = signal(false);
  readonly running = signal(false);
  readonly error = signal<string | null>(null);
  readonly result = signal<AutoApplyRunResult | null>(null);
  readonly isAuto = computed(() => this.config().mode === 'AUTO');

  private readonly service = inject(JobCopilotService);

  constructor() {
    this.service.getAutoApplyConfig().subscribe({
      next: config => this.config.set({ ...DEFAULT_CONFIG, ...config }),
      error: () => this.config.set(DEFAULT_CONFIG),
    });
  }

  patch(partial: Partial<AutoApplyConfig>): void {
    this.config.update(c => ({ ...c, ...partial }));
    this.savedOk.set(false);
  }

  setMode(mode: AutoApplyMode): void {
    this.patch({ mode });
  }

  save(): void {
    this.saving.set(true);
    this.error.set(null);
    this.service.saveAutoApplyConfig(this.config()).subscribe({
      next: saved => {
        this.config.set({ ...DEFAULT_CONFIG, ...saved });
        this.saving.set(false);
        this.savedOk.set(true);
      },
      error: err => {
        this.error.set(err.error?.message ?? 'Failed to save configuration');
        this.saving.set(false);
      },
    });
  }

  run(): void {
    this.running.set(true);
    this.error.set(null);
    this.result.set(null);
    // Persist current config first so the run uses the on-screen guardrails.
    this.service.saveAutoApplyConfig(this.config()).subscribe({
      next: () =>
        this.service.runAutoApply().subscribe({
          next: result => {
            this.result.set(result);
            this.running.set(false);
          },
          error: err => {
            this.error.set(err.error?.message ?? 'Auto-apply run failed');
            this.running.set(false);
          },
        }),
      error: err => {
        this.error.set(err.error?.message ?? 'Failed to save configuration');
        this.running.set(false);
      },
    });
  }
}
