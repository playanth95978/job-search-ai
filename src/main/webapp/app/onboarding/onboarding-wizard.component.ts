import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { catchError, finalize, forkJoin, of, switchMap } from 'rxjs';

import { TranslateDirective } from 'app/shared/language';
import { AccountService } from 'app/core/auth/account.service';
import { JobCopilotService } from 'app/job-copilot/shared/job-copilot.service';
import { CandidateProfile, UserPreference, UserPreferenceDto } from 'app/job-copilot/shared/job-copilot.model';

import { Stepper } from 'app/shared/stepper/stepper';
import { StepComponent } from 'app/shared/stepper/step/step';
import { JhiStepContent } from 'app/shared/stepper/step-content.directive';

import { WelcomeUploadStepComponent } from './steps/welcome-upload-step.component';
import { ProfileStepComponent } from './steps/profile-step.component';
import { PreferencesStepComponent } from './steps/preferences-step.component';
import { SummaryStepComponent } from './steps/summary-step.component';

/**
 * First-login onboarding wizard. Orchestrates the shared {@link Stepper} with four steps
 * (welcome+CV upload, profile, preferences, summary). On finish it persists the preferences,
 * marks onboarding complete server-side, refreshes the account and lands on the dashboard.
 */
@Component({
  selector: 'jhi-onboarding-wizard',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    TranslateModule,
    TranslateDirective,
    FaIconComponent,
    Stepper,
    StepComponent,
    JhiStepContent,
    WelcomeUploadStepComponent,
    ProfileStepComponent,
    PreferencesStepComponent,
    SummaryStepComponent,
  ],
  templateUrl: './onboarding-wizard.component.html',
  styleUrl: './onboarding-wizard.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OnboardingWizardComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly jobCopilot = inject(JobCopilotService);
  private readonly accountService = inject(AccountService);
  private readonly router = inject(Router);

  readonly profile = signal<CandidateProfile | null>(null);
  readonly submitting = signal(false);

  // Empty groups → always-valid steps (welcome & summary).
  readonly welcomeForm = this.fb.group({});
  readonly summaryForm = this.fb.group({});

  readonly profileForm = this.fb.group({
    firstName: ['', Validators.required],
    lastName: [''],
    title: [''],
    location: [''],
  });

  readonly prefsForm = this.fb.group({
    preferredRoles: [[] as string[]],
    preferredLocations: [[] as string[]],
    excludedTechnologies: [[] as string[]],
    salaryMin: [null as number | null],
    salaryMax: [null as number | null],
    remoteOnly: [false],
    contractType: [''],
  });

  ngOnInit(): void {
    // Best-effort prefill in a single subscription. Each source is guarded with catchError so a
    // 404 for a brand-new user doesn't short-circuit forkJoin and drop the other result.
    forkJoin({
      profile: this.jobCopilot.getProfile().pipe(catchError(() => of(null))),
      prefs: this.jobCopilot.getPreferences().pipe(catchError(() => of(null))),
    }).subscribe(({ profile, prefs }) => {
      if (profile) this.applyProfile(profile);
      if (prefs) this.applyPrefs(prefs);
    });
  }

  onUploaded(p: CandidateProfile): void {
    this.applyProfile(p);
  }

  finish(): void {
    if (this.submitting()) return;
    this.submitting.set(true);
    this.jobCopilot
      .updatePreferences(this.buildPrefs())
      .pipe(
        catchError(() => of(null)),
        switchMap(() => this.accountService.completeOnboarding().pipe(catchError(() => of(null)))),
        switchMap(() => this.accountService.identity(true).pipe(catchError(() => of(null)))),
        // Always release the guard so the button stays usable if navigation didn't occur.
        finalize(() => this.submitting.set(false)),
      )
      .subscribe(() => this.router.navigate(['/job-copilot']));
  }

  private applyProfile(p: CandidateProfile): void {
    this.profile.set(p);
    const parts = (p.fullName ?? '').trim().split(/\s+/).filter(Boolean);
    const first = parts.shift() ?? '';
    const profileFormsControls = this.profileForm.controls;
    this.profileForm.patchValue({
      firstName: profileFormsControls.firstName.value ?? first,
      lastName: profileFormsControls.lastName.value ?? parts.join(' '),
      title: profileFormsControls.title.value ?? p.preferredRoles?.[0] ?? '',
      location: profileFormsControls.location.value ?? p.location ?? '',
    });
  }

  private applyPrefs(pref: UserPreference): void {
    const toArr = (s?: string): string[] =>
      s
        ? s
            .split(/[,\n]/)
            .map(x => x.trim())
            .filter(Boolean)
        : [];
    this.prefsForm.patchValue({
      preferredRoles: toArr(pref.preferredRoles),
      preferredLocations: toArr(pref.preferredLocations),
      excludedTechnologies: toArr(pref.excludedTechnologies),
      salaryMin: pref.salaryMin ?? null,
      salaryMax: pref.salaryMax ?? null,
      remoteOnly: pref.remoteOnly ?? false,
      contractType: pref.contractType ?? '',
    });
  }

  private buildPrefs(): UserPreferenceDto {
    const v = this.prefsForm.getRawValue();
    return {
      remoteOnly: v.remoteOnly ?? false,
      contractType: v.contractType ?? undefined,
      salaryMin: v.salaryMin ?? undefined,
      salaryMax: v.salaryMax ?? undefined,
      preferredRoles: v.preferredRoles ?? [],
      excludedTechnologies: v.excludedTechnologies ?? [],
      preferredLocations: v.preferredLocations ?? [],
    };
  }
}
