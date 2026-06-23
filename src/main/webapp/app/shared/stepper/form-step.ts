import { Directive, input } from '@angular/core';
import { FormGroup } from '@angular/forms';

export abstract class FormStep {
  abstract formGroup?: FormGroup | null;
  abstract markTouched(): void;
  abstract isValid(): boolean;
  onStepIn?(): void;
}

@Directive({
  selector: '[appFormStep]',
  standalone: true,
  providers: [{ provide: FormStep, useExisting: FormStepDirective }],
})
export class FormStepDirective extends FormStep {
  stepId = input<string>('appFormStep');

  formGroup!: FormGroup;
  markTouched(): void {}
  isValid(): boolean {
    return true;
  }
}
