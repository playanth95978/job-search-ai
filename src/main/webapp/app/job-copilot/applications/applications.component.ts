import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { JobCopilotService } from '../shared/job-copilot.service';
import { ApplyModalComponent } from '../shared/apply-modal/apply-modal.component';
import { ApplicationStatus, JobApplication } from '../shared/job-copilot.model';

@Component({
  selector: 'jhi-job-copilot-applications',
  standalone: true,
  imports: [CommonModule, FormsModule, FaIconComponent, ApplyModalComponent, TranslateModule],
  templateUrl: './applications.component.html',
  styleUrl: './applications.component.scss',
})
export class ApplicationsComponent implements OnInit {
  private service = inject(JobCopilotService);
  private translateService = inject(TranslateService);

  applications = signal<JobApplication[]>([]);
  statuses: ApplicationStatus[] = ['DRAFT', 'APPLIED', 'INTERVIEW', 'REJECTED', 'GHOSTED', 'OFFER', 'WITHDRAWN'];

  selectedApplication = signal<JobApplication | null>(null);

  ngOnInit(): void {
    this.loadApplications();
  }

  loadApplications(): void {
    this.service.getApplications().subscribe({
      next: apps => this.applications.set(apps),
      error: () => {},
    });
  }

  updateStatus(id: string, status: string): void {
    this.service.updateApplicationStatus(id, status).subscribe({
      next: () => this.loadApplications(),
      error: err => alert('Error: ' + (err.error?.message || 'Failed')),
    });
  }

  openApplyModal(app: JobApplication): void {
    this.selectedApplication.set(app);
  }

  closeApplyModal(): void {
    this.selectedApplication.set(null);
  }

  onApplied(): void {
    this.selectedApplication.set(null);
    this.loadApplications();
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
}
