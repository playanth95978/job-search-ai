import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, inject, signal } from '@angular/core';

import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { NgbPagination } from '@ng-bootstrap/ng-bootstrap/pagination';
import { Subscription, finalize, interval } from 'rxjs';

import { ITEMS_PER_PAGE, TOTAL_COUNT_RESPONSE_HEADER } from 'app/config/pagination.constants';
import { ItemCount } from 'app/shared/pagination';
import { JobExecution, JobSummary } from './batch.model';
import { BatchService } from './batch.service';

@Component({
  selector: 'jhi-batch',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './batch.html',
  styleUrl: './batch.scss',
  imports: [DatePipe, DecimalPipe, FontAwesomeModule, NgbPagination, ItemCount],
})
export default class Batch implements OnInit, OnDestroy {
  readonly jobs = signal<JobSummary[]>([]);
  readonly executions = signal<JobExecution[]>([]);
  readonly selectedJob = signal<string | undefined>(undefined);
  readonly loadingExecutions = signal(false);
  readonly launching = signal(false);
  readonly expanded = signal<number | undefined>(undefined);

  readonly totalItems = signal(0);
  readonly itemsPerPage = signal(ITEMS_PER_PAGE);
  page = signal(1);

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
    this.page.set(1);
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

  refreshExecutions(): void {
    const name = this.selectedJob();
    if (!name) {
      return;
    }
    this.batchService
      .executions(name, {
        page: this.page() - 1,
        size: this.itemsPerPage(),
      })
      .pipe(finalize(() => this.loadingExecutions.set(false)))
      .subscribe({
        next: (res: HttpResponse<JobExecution[]>) => {
          this.onSuccess(res.body, res.headers);
        },
        error: () => this.executions.set([]),
      });
  }

  private onSuccess(executions: JobExecution[] | null, headers: HttpHeaders): void {
    this.totalItems.set(Number(headers.get(TOTAL_COUNT_RESPONSE_HEADER)));
    this.executions.set(executions ?? []);
  }
}
