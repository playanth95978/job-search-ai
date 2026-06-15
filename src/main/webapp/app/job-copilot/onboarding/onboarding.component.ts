import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { JobCopilotService } from '../shared/job-copilot.service';
import { CandidateProfile } from '../shared/job-copilot.model';
import { CvQualityComponent } from '../shared/cv-quality/cv-quality.component';

@Component({
  selector: 'jhi-job-copilot-onboarding',
  standalone: true,
  imports: [CommonModule, FaIconComponent, CvQualityComponent],
  templateUrl: './onboarding.component.html',
  styleUrl: './onboarding.component.scss',
})
export class OnboardingComponent {
  private service = inject(JobCopilotService);
  private router = inject(Router);

  uploading = signal(false);
  profile = signal<CandidateProfile | null>(null);
  error = signal<string | null>(null);
  isDragOver = signal(false);

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.isDragOver.set(true);
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.isDragOver.set(false);
    const file = event.dataTransfer?.files[0];
    if (file) this.upload(file);
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (file) this.upload(file);
  }

  private upload(file: File): void {
    this.uploading.set(true);
    this.error.set(null);
    this.service.uploadCv(file).subscribe({
      next: p => {
        this.profile.set(p);
        this.uploading.set(false);
      },
      error: err => {
        this.error.set(err.error?.message || 'Failed to process CV');
        this.uploading.set(false);
      },
    });
  }

  /** Accepts a structured array (DTO) or a legacy JSON/comma string. */
  private toArray(value: unknown): any[] {
    if (Array.isArray(value)) return value;
    if (typeof value === 'string' && value.trim()) {
      try {
        const parsed = JSON.parse(value);
        return Array.isArray(parsed) ? parsed : [];
      } catch {
        return value
          .split(',')
          .map(s => s.trim())
          .filter(Boolean);
      }
    }
    return [];
  }

  parseSkills(value?: unknown): { name: string }[] {
    return this.toArray(value)
      .map(s => (typeof s === 'string' ? { name: s } : s))
      .filter(s => s?.name);
  }

  parseEducation(value?: unknown): any[] {
    return this.toArray(value);
  }

  parseCertifications(value?: unknown): any[] {
    return this.toArray(value);
  }

  parseExperiences(value?: unknown): any[] {
    return this.toArray(value);
  }

  goToDashboard(): void {
    this.router.navigate(['/job-copilot']);
  }
}
