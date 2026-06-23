import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { TranslateDirective } from 'app/shared/language';

import { StepHeroComponent } from 'app/shared/onboarding-ui/step-hero/step-hero.component';
import { ChipInputComponent } from 'app/shared/onboarding-ui/chip-input/chip-input.component';

/** Onboarding step: job search preferences (roles, locations, salary, remote, contract). */
@Component({
  selector: 'jhi-preferences-step',
  standalone: true,
  imports: [ReactiveFormsModule, TranslateModule, TranslateDirective, StepHeroComponent, ChipInputComponent],
  templateUrl: './preferences-step.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PreferencesStepComponent {
  readonly form = input.required<FormGroup>();
}
