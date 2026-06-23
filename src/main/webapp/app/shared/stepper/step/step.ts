import { ChangeDetectionStrategy, Component, contentChild, input } from '@angular/core';
import { JhiStepContent } from '../step-content.directive';
import { FormGroup } from '@angular/forms';
import { FormStep } from '../form-step';

@Component({
  selector: 'jhi-step',
  standalone: true,
  imports: [],
  template: '',
  providers: [{ provide: FormStep, useExisting: StepComponent }],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StepComponent extends FormStep {
  stepId = input.required<string>();
  /** Optional human-friendly label shown in the progress header (falls back to stepId). */
  label = input<string>('');
  formGroupInput = input<FormGroup | undefined>(undefined);
  private manualFormGroup?: FormGroup;
  private defaultFormGroup = new FormGroup({});

  content = contentChild(JhiStepContent);

  override get formGroup(): FormGroup {
    return this.manualFormGroup ?? this.formGroupInput() ?? this.defaultFormGroup;
  }

  registerFormGroup(fg: FormGroup): void {
    this.manualFormGroup = fg;
  }

  override markTouched(): void {
    this.formGroup.markAllAsTouched();
  }

  override isValid(): boolean {
    const content = this.content();
    // On pourrait essayer de trouver si le contenu injecté est une FormStep
    // Mais StepComponent ne sait pas ce qu'il y a dans son template car c'est géré par Stepper
    return this.formGroup.valid;
  }
}
