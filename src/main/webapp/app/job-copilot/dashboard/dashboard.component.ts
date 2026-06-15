import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language';
import { TranslateModule } from '@ngx-translate/core';
import { JobCopilotService } from '../shared/job-copilot.service';
import { applicationStatusColor, applicationStatusIcon, ApplicationStats, CandidateProfile } from '../shared/job-copilot.model';
import { CvQualityComponent } from '../shared/cv-quality/cv-quality.component';
import { candidateProfileToResume } from "../cv-builder/candidate-profile.mapper";

@Component({
  selector: 'jhi-job-copilot-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule, FaIconComponent, TranslateDirective, TranslateModule, CvQualityComponent],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardComponent implements OnInit {
  private service = inject(JobCopilotService);

  profile = signal<CandidateProfile | null>(null);
  stats = signal<ApplicationStats>({});
  statsEntries = signal<[string, number][]>([]);
  loading = signal(true);

  ngOnInit(): void {
    console.log("init")
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
    this.service.getApplicationStats().subscribe({
      next: s => {
        this.stats.set(s);
        this.statsEntries.set(Object.entries(s).filter(([, v]) => v > 0));
      },
      error: () => {},
    });
  }

  getStatusIcon(status: string): string {
    return applicationStatusIcon(status);
  }

  getStatusColor(status: string): string {
    return applicationStatusColor(status);
  }
}
