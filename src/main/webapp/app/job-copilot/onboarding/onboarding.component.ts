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

  parseSkills(skills?: string): string[] {
    if (!skills) return [];
    try {
      return JSON.parse(skills);
    } catch {
      return skills
        .split(',')
        .map(s => s.trim())
        .filter(s => s.length > 0);
    }
  }

  parseExperiences(exp?: string): { company: string; role: string }[] {
    if (!exp) return [];
    try {
      return JSON.parse(exp);
    } catch {
      return [];
    }
  }

  goToDashboard(): void {
    this.router.navigate(['/job-copilot']);
  }
}
