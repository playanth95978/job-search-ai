import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { TranslateDirective } from 'app/shared/language';

import { StepHeroComponent } from 'app/shared/onboarding-ui/step-hero/step-hero.component';

/** Onboarding step: confirm/edit profile basics (prefilled from the extracted CV). */
@Component({
  selector: 'jhi-profile-step',
  standalone: true,
  imports: [ReactiveFormsModule, TranslateModule, TranslateDirective, StepHeroComponent],
  templateUrl: './profile-step.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProfileStepComponent {
  readonly form = input.required<FormGroup>();
}
