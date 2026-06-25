import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CdkDragDrop, DragDropModule, moveItemInArray, transferArrayItem } from '@angular/cdk/drag-drop';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { JobCopilotService } from '../shared/job-copilot.service';
import { ApplyModalComponent } from '../shared/apply-modal/apply-modal.component';
import { ApplicationStatus, JobApplication } from '../shared/job-copilot.model';

interface KanbanColumn {
  status: ApplicationStatus;
  items: JobApplication[];
}

@Component({
  selector: 'jhi-job-copilot-applications',
  standalone: true,
  imports: [CommonModule, FormsModule, DragDropModule, FaIconComponent, TranslateModule],
  templateUrl: './applications.component.html',
  styleUrl: './applications.component.scss',
})
export class ApplicationsComponent implements OnInit {
  private service = inject(JobCopilotService);
  private translateService = inject(TranslateService);
  private modalService = inject(NgbModal);

  // Pipeline order, left to right.
  readonly statuses: ApplicationStatus[] = ['DRAFT', 'APPLIED', 'INTERVIEW', 'OFFER', 'REJECTED', 'GHOSTED', 'WITHDRAWN'];

  readonly board = signal<KanbanColumn[]>([]);
  readonly total = computed(() => this.board().reduce((sum, col) => sum + col.items.length, 0));
  readonly dropListIds = this.statuses.map(s => 'col-' + s);

  ngOnInit(): void {
    this.loadApplications();
  }

  loadApplications(): void {
    this.service.getApplications().subscribe({
      next: apps => this.board.set(this.bucket(apps)),
      error: () => this.board.set(this.bucket([])),
    });
  }

  /** Drag-and-drop between columns persists the new status; reorders within a column locally only. */
  drop(event: CdkDragDrop<JobApplication[]>, targetStatus: ApplicationStatus): void {
    if (event.previousContainer === event.container) {
      moveItemInArray(event.container.data, event.previousIndex, event.currentIndex);
      this.board.set([...this.board()]);
      return;
    }
    const moved = event.previousContainer.data[event.previousIndex];
    const previousStatus = moved.status;
    transferArrayItem(event.previousContainer.data, event.container.data, event.previousIndex, event.currentIndex);
    moved.status = targetStatus;
    this.board.set([...this.board()]);

    this.service.updateApplicationStatus(moved.id!, targetStatus).subscribe({
      next: updated => {
        moved.appliedAt = updated.appliedAt;
        moved.updatedAt = updated.updatedAt;
        this.board.set([...this.board()]);
      },
      error: err => {
        // revert on failure
        moved.status = previousStatus;
        this.loadApplications();
        alert('Error: ' + (err.error?.message || 'Failed'));
      },
    });
  }

  openApplyModal(app: JobApplication): void {
    const modalRef = this.modalService.open(ApplyModalComponent, { size: 'xl', backdrop: 'static', scrollable: true });
    modalRef.componentInstance.existingApplication.set(app);
    modalRef.componentInstance.applied.subscribe(() => this.loadApplications());
  }

  getStatusIcon(status: string): string {
    const icons: Record<string, string> = {
      DRAFT: 'file-alt',
      APPLIED: 'paper-plane',
      INTERVIEW: 'user-tie',
      REJECTED: 'circle-xmark',
      GHOSTED: 'ghost',
      OFFER: 'handshake',
      WITHDRAWN: 'right-from-bracket',
    };
    return icons[status] || 'circle-info';
  }

  getStatusColor(status: string): string {
    const colors: Record<string, string> = {
      DRAFT: '#94a3b8',
      APPLIED: '#3b82f6',
      INTERVIEW: '#8b5cf6',
      REJECTED: '#ef4444',
      GHOSTED: '#f59e0b',
      OFFER: '#10b981',
      WITHDRAWN: '#6b7280',
    };
    return colors[status] || '#64748b';
  }

  private bucket(apps: JobApplication[]): KanbanColumn[] {
    return this.statuses.map(status => ({
      status,
      items: apps.filter(a => a.status === status),
    }));
  }
}
