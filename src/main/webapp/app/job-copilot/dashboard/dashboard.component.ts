import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language';
import { TranslateModule } from '@ngx-translate/core';
import { JobCopilotService } from '../shared/job-copilot.service';
import { applicationStatusColor, applicationStatusIcon, ApplicationStats, CandidateProfile } from '../shared/job-copilot.model';
import { CvQualityComponent } from '../shared/cv-quality/cv-quality.component';
import { CvDropzoneComponent } from '../onboarding/cv-dropzone/cv-dropzone.component';

@Component({
  selector: 'jhi-job-copilot-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule, FaIconComponent, TranslateDirective, TranslateModule, CvQualityComponent, CvDropzoneComponent],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardComponent implements OnInit {
  private readonly service = inject(JobCopilotService);
  private readonly route = inject(ActivatedRoute);

  readonly profile = signal<CandidateProfile | null>(null);
  readonly stats = signal<ApplicationStats>({});
  readonly statsEntries = signal<[string, number][]>([]);
  readonly radarUnread = signal(0);
  readonly loading = signal(true);

  readonly showDropzone = signal(false);
  readonly uploading = signal(false);
  readonly uploadSuccess = signal(false);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    if (this.route.snapshot.queryParamMap.get('mode') === 'upload') {
      this.showDropzone.set(true);
    }
    this.service.radarUnreadCount().subscribe({
      next: r => this.radarUnread.set(r.count),
      error() {},
    });
    this.loadProfile();
    this.loadStats();
  }

  loadProfile(): void {
    this.service.getProfile().subscribe({
      next: p => {
        this.profile.set(p);
        this.loading.set(false);
      },
      error: () => {
        this.profile.set(null);
        this.loading.set(false);
      },
    });
  }

  loadStats(): void {
    this.service.getApplicationStats().subscribe({
      next: s => {
        this.stats.set(s);
        this.statsEntries.set(Object.entries(s).filter(([, v]) => v > 0));
      },
      error() {},
    });
  }

  toggleDropzone(): void {
    this.showDropzone.update(v => !v);
    this.error.set(null);
    this.uploadSuccess.set(false);
  }

  upload(file: File): void {
    this.uploading.set(true);
    this.error.set(null);
    this.uploadSuccess.set(false);
    this.service.uploadCv(file).subscribe({
      next: p => {
        this.profile.set(p);
        this.uploading.set(false);
        this.showDropzone.set(false);
        this.uploadSuccess.set(true);
        this.loadStats();
        setTimeout(() => this.uploadSuccess.set(false), 5000);
      },
      error: err => {
        this.error.set(err.error?.message ?? 'Failed to process CV');
        this.uploading.set(false);
      },
    });
  }

  getStatusIcon(status: string): string {
    return applicationStatusIcon(status);
  }

  getStatusColor(status: string): string {
    return applicationStatusColor(status);
  }
}
