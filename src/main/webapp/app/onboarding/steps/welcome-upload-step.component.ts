import { ChangeDetectionStrategy, Component, inject, output, signal } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateModule } from '@ngx-translate/core';
import { TranslateDirective } from 'app/shared/language';

import { JobCopilotService } from 'app/job-copilot/shared/job-copilot.service';
import { CandidateProfile } from 'app/job-copilot/shared/job-copilot.model';
import { StepHeroComponent } from 'app/shared/onboarding-ui/step-hero/step-hero.component';

/** First onboarding step: welcome + CV upload (drag/drop). Emits the extracted profile upward. */
@Component({
  selector: 'jhi-welcome-upload-step',
  standalone: true,
  imports: [FaIconComponent, TranslateModule, TranslateDirective, StepHeroComponent],
  templateUrl: './welcome-upload-step.component.html',
  styleUrl: './welcome-upload-step.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WelcomeUploadStepComponent {
  private readonly service = inject(JobCopilotService);

  readonly uploaded = output<CandidateProfile>();

  readonly uploading = signal(false);
  readonly profile = signal<CandidateProfile | null>(null);
  readonly error = signal<string | null>(null);
  readonly isDragOver = signal(false);

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
        this.uploaded.emit(p);
      },
      error: err => {
        this.error.set(err.error?.message || 'Failed to process CV');
        this.uploading.set(false);
      },
    });
  }
}
