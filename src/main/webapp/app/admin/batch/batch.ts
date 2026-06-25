import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, inject, signal } from '@angular/core';

import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { Subscription, finalize, interval } from 'rxjs';

import { JobExecution, JobSummary } from './batch.model';
import { BatchService } from './batch.service';

@Component({
  selector: 'jhi-batch',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './batch.html',
  styleUrl: './batch.scss',
  imports: [DatePipe, DecimalPipe, FontAwesomeModule],
})
export default class Batch implements OnInit, OnDestroy {
  readonly jobs = signal<JobSummary[]>([]);
  readonly executions = signal<JobExecution[]>([]);
  readonly selectedJob = signal<string | undefined>(undefined);
  readonly loadingExecutions = signal(false);
  readonly launching = signal(false);
  readonly expanded = signal<number | undefined>(undefined);

  private readonly batchService = inject(BatchService);
  private pollSub?: Subscription;

  ngOnInit(): void {
    this.loadJobs();
    // refresh the selected job's executions every 5s so running jobs update live
    this.pollSub = interval(5000).subscribe(() => {
      if (this.selectedJob()) {
        this.refreshExecutions();
        this.loadJobs();
      }
    });
  }

  ngOnDestroy(): void {
    this.pollSub?.unsubscribe();
  }

  selectJob(name: string): void {
    this.selectedJob.set(name);
    this.expanded.set(undefined);
    this.loadingExecutions.set(true);
    this.refreshExecutions();
  }

  launch(name: string): void {
    this.launching.set(true);
    this.batchService
      .execute(name)
      .pipe(finalize(() => this.launching.set(false)))
      .subscribe(() => {
        this.selectJob(name);
        this.loadJobs();
      });
  }

  toggle(executionId: number): void {
    this.expanded.set(this.expanded() === executionId ? undefined : executionId);
  }

  statusClass(status?: string): string {
    switch (status) {
      case 'COMPLETED':
        return 'badge bg-success';
      case 'STARTED':
      case 'STARTING':
        return 'badge bg-info';
      case 'FAILED':
      case 'ABANDONED':
        return 'badge bg-danger';
      case 'STOPPED':
      case 'STOPPING':
        return 'badge bg-warning text-dark';
      default:
        return 'badge bg-secondary';
    }
  }

  formatDuration(ms?: number): string {
    if (ms == null) {
      return '—';
    }
    const totalSeconds = Math.round(ms / 1000);
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    return minutes > 0 ? `${minutes}m ${seconds}s` : `${seconds}s`;
  }

  private loadJobs(): void {
    this.batchService.jobs().subscribe(jobs => {
      this.jobs.set(jobs);
      if (!this.selectedJob() && jobs.length > 0) {
        this.selectJob(jobs[0].name);
      }
    });
  }

  private refreshExecutions(): void {
    const name = this.selectedJob();
    if (!name) {
      return;
    }
    this.batchService
      .executions(name)
      .pipe(finalize(() => this.loadingExecutions.set(false)))
      .subscribe({
        next: executions => this.executions.set(executions),
        error: () => this.executions.set([]),
      });
  }
}
