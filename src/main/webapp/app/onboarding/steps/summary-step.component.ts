import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { TranslateDirective } from 'app/shared/language';

import { CandidateProfile } from 'app/job-copilot/shared/job-copilot.model';
import { CvQualityComponent } from 'app/job-copilot/shared/cv-quality/cv-quality.component';
import { StepHeroComponent } from 'app/shared/onboarding-ui/step-hero/step-hero.component';

/** Onboarding step: recap of the extracted profile (jhi-cv-quality) and the chosen preferences. */
@Component({
  selector: 'jhi-summary-step',
  standalone: true,
  imports: [TranslateModule, TranslateDirective, CvQualityComponent, StepHeroComponent],
  templateUrl: './summary-step.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SummaryStepComponent {
  readonly profile = input<CandidateProfile | null>(null);
  /** Preferences recap chips (roles + locations). */
  readonly roles = input<string[]>([]);
  readonly locations = input<string[]>([]);
}
