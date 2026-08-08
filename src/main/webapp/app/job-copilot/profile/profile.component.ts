import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateModule } from '@ngx-translate/core';

import { TranslateDirective } from 'app/shared/language';
import { JobCopilotService } from '../shared/job-copilot.service';
import { CandidateProfile } from '../shared/job-copilot.model';
import { CvQualityComponent } from '../shared/cv-quality/cv-quality.component';
import { AtsSearchabilityComponent } from '../shared/ats-searchability/ats-searchability.component';
import { ProfileEditorComponent } from '../onboarding/profile-editor/profile-editor.component';

/**
 * "My profile" page — distinct from onboarding. Shows the extracted profile in detail plus its CV
 * quality and ATS searchability. When the user has no CV on file yet (first login), it invites them
 * to upload one (onboarding). Editing reuses {@link ProfileEditorComponent}.
 */
@Component({
  selector: 'jhi-job-copilot-profile',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    FaIconComponent,
    TranslateModule,
    TranslateDirective,
    CvQualityComponent,
    AtsSearchabilityComponent,
    ProfileEditorComponent,
  ],
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.scss',
})
export class ProfileComponent {
  private readonly service = inject(JobCopilotService);

  readonly loading = signal(true);
  readonly profile = signal<CandidateProfile | null>(null);
  readonly editing = signal(false);
  readonly saving = signal(false);
  readonly saveError = signal<string | null>(null);
  readonly editProfile = signal<CandidateProfile>({});

  readonly hasCv = computed(() => this.profile() !== null);

  constructor() {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.service.getProfile().subscribe({
      next: profile => {
        this.profile.set(profile);
        this.loading.set(false);
      },
      // 404 = the user has no CV profile yet.
      error: () => {
        this.profile.set(null);
        this.loading.set(false);
      },
    });
  }

  startEdit(): void {
    const current = this.profile();
    if (!current) return;
    this.editProfile.set(structuredClone(current));
    this.saveError.set(null);
    this.editing.set(true);
  }

  cancelEdit(): void {
    this.editing.set(false);
  }

  save(): void {
    this.saving.set(true);
    this.saveError.set(null);
    this.service.updateProfile(this.editProfile()).subscribe({
      next: profile => {
        this.profile.set(profile);
        this.saving.set(false);
        this.editing.set(false);
      },
      error: err => {
        this.saveError.set(err.error?.message ?? 'Failed to save profile');
        this.saving.set(false);
      },
    });
  }
}
