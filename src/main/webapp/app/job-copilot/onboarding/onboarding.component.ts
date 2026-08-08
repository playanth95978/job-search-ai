import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateModule } from '@ngx-translate/core';
import { switchMap } from 'rxjs/operators';

import { TranslateDirective } from 'app/shared/language';
import { JobCopilotService } from '../shared/job-copilot.service';
import { CandidateProfile, UserPreferenceDto } from '../shared/job-copilot.model';
import { CvQualityComponent } from '../shared/cv-quality/cv-quality.component';
import { CvDropzoneComponent } from './cv-dropzone/cv-dropzone.component';
import { ProfileEditorComponent } from './profile-editor/profile-editor.component';
import { PreferencesEditorComponent } from './preferences-editor/preferences-editor.component';

const EMPTY_PREFERENCES: UserPreferenceDto = {
  preferredRoles: [],
  excludedTechnologies: [],
  preferredLocations: [],
};

/**
 * Onboarding orchestrator. Signal-first container that drives the flow (upload → edit → save) and
 * delegates each concern to a standalone sub-component: {@link CvDropzoneComponent} for upload,
 * {@link ProfileEditorComponent} and {@link PreferencesEditorComponent} for the editable state.
 * The editable profile and preferences are the two source-of-truth signals bound down into the
 * editors and read back on save.
 */
@Component({
  selector: 'jhi-job-copilot-onboarding',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FaIconComponent,
    TranslateModule,
    TranslateDirective,
    CvQualityComponent,
    CvDropzoneComponent,
    ProfileEditorComponent,
    PreferencesEditorComponent,
  ],
  templateUrl: './onboarding.component.html',
  styleUrl: './onboarding.component.scss',
})
export class OnboardingComponent {
  private readonly service = inject(JobCopilotService);
  private readonly router = inject(Router);

  readonly uploading = signal(false);
  readonly profile = signal<CandidateProfile | null>(null);
  readonly error = signal<string | null>(null);

  readonly saving = signal(false);
  readonly saveError = signal<string | null>(null);

  /** Source-of-truth signals bound into the editor sub-components. */
  readonly editProfile = signal<CandidateProfile>({});
  readonly prefs = signal<UserPreferenceDto>(EMPTY_PREFERENCES);

  upload(file: File): void {
    this.uploading.set(true);
    this.error.set(null);
    this.service.uploadCv(file).subscribe({
      next: p => {
        this.profile.set(p);
        this.editProfile.set(structuredClone(p));
        this.loadPreferences();
        this.uploading.set(false);
      },
      error: err => {
        this.error.set(err.error?.message ?? 'Failed to process CV');
        this.uploading.set(false);
      },
    });
  }

  /** Loads existing preferences (if any) and seeds preferred locations from the profile when empty. */
  private loadPreferences(): void {
    this.service.getPreferences().subscribe({
      next: p =>
        this.prefs.set(
          this.withSeededLocation({
            remoteOnly: p.remoteOnly ?? false,
            contractType: p.contractType ?? undefined,
            salaryMin: p.salaryMin ?? undefined,
            salaryMax: p.salaryMax ?? undefined,
            preferredRoles: this.parseJsonList(p.preferredRoles),
            excludedTechnologies: this.parseJsonList(p.excludedTechnologies),
            preferredLocations: this.parseJsonList(p.preferredLocations),
          }),
        ),
      error: () => this.prefs.set(this.withSeededLocation({ ...EMPTY_PREFERENCES })),
    });
  }

  private withSeededLocation(prefs: UserPreferenceDto): UserPreferenceDto {
    const location = this.editProfile().location;
    if (prefs.preferredLocations.length === 0 && location) {
      return { ...prefs, preferredLocations: [location] };
    }
    return prefs;
  }

  private parseJsonList(value?: string): string[] {
    if (!value) return [];
    try {
      const parsed: unknown = JSON.parse(value);
      return Array.isArray(parsed) ? (parsed as string[]) : [];
    } catch {
      return [];
    }
  }

  /** Persists the edited profile then the preferences, then heads to the dashboard. */
  saveAndContinue(): void {
    this.saving.set(true);
    this.saveError.set(null);
    // Keep preference roles in sync with the profile's target roles (edited in the profile editor).
    const dto: UserPreferenceDto = { ...this.prefs(), preferredRoles: this.editProfile().preferredRoles ?? [] };
    this.service
      .updateProfile(this.editProfile())
      .pipe(switchMap(() => this.service.updatePreferences(dto)))
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.goToDashboard();
        },
        error: err => {
          this.saveError.set(err.error?.message || 'Failed to save profile');
          this.saving.set(false);
        },
      });
  }

  goToDashboard(): void {
    this.router.navigate(['/job-copilot']);
  }
}
