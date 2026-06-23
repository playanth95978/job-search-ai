import {
  ChangeDetectionStrategy,
  Component,
  computed,
  contentChildren,
  effect,
  inject,
  input,
  output,
  signal,
  viewChild,
  ViewEncapsulation,
} from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { StepComponent } from './step/step';
import { FormStep } from './form-step';
import { Router } from '@angular/router';

@Component({
  selector: 'jhi-stepper',
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './stepper.html',
  styleUrl: './stepper.scss',
  encapsulation: ViewEncapsulation.None,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Stepper {
  private fb = inject(FormBuilder);

  steps = contentChildren(StepComponent, { descendants: true });
  activeStepContent = viewChild(FormStep, { read: FormStep });
  activeStepComponent = viewChild(StepComponent);

  // Navigation button labels (overridable; French defaults to match the app).
  backLabel = input('Retour');
  nextLabel = input('Continuer');
  finishLabel = input('Terminer');
  /** When true (default) the stepper navigates to the home route on finish. Set false to let the host handle it via (finished). */
  autoNavigate = input(true);

  /** Emitted when the user validates the last step. */
  finished = output<void>();

  currentStepIndex = signal(0);
  rootForm: FormGroup = this.fb.group({});
  router: Router = inject(Router);

  /** Friendly header label for a step (its `label` input, falling back to its id). */
  stepLabel(step: StepComponent, _index: number): string {
    return step.label() || step.stepId();
  }

  constructor() {
    effect(() => {
      // Trigger onStepIn if available on the effective form
      const effective = this.effectiveForm();
      if (effective?.onStepIn) {
        effective.onStepIn();
      }
    });
  }

  currentStep = computed(() => {
    const steps = this.steps();
    const index = this.currentStepIndex();
    return steps[index];
  });

  activeFormGroup = computed(() => {
    const active = this.activeStepContent();
    const step = this.currentStep();
    // Si on a un contenu actif (rendu) et que c'est une FormStep différente du proxy StepComponent
    if (active && active !== step) {
      return active.formGroup;
    }

    return step.formGroup || new FormGroup({});
  });

  allStepsData = computed(() => {
    // Force dependency on step changes to ensure fresh data from getRawValue()
    this.currentStepIndex();
    const steps = this.steps();
    const data: Record<string, any> = {};
    steps.forEach(step => {
      data[step.stepId()] = step.formGroup.getRawValue();
    });
    return data;
  });

  isFirstStep = computed(() => this.currentStepIndex() === 0);
  isLastStep = computed(() => this.currentStepIndex() === this.steps().length - 1);

  effectiveForm = computed(() => {
    const active = this.activeStepContent();
    const activeComp = this.activeStepComponent();
    const steps = this.steps();
    const step = steps[this.currentStepIndex()];

    // Si on a un contenu actif injecté, on le préfère car il contient la logique métier (isValid override)
    // On s'assure qu'il n'est pas identique au StepComponent (le conteneur)
    if (active && active !== step && active !== activeComp) {
      return active;
    }
    return step;
  });

  next(): void {
    const effectiveForm = this.effectiveForm();

    if (effectiveForm?.isValid()) {
      if (!this.isLastStep()) {
        this.currentStepIndex.update(i => i + 1);
      } else {
        this.finished.emit();
        if (this.autoNavigate()) {
          this.router.navigate(['/']);
        }
      }
    } else {
      effectiveForm?.markTouched();
    }
  }

  prev(): void {
    if (!this.isFirstStep()) {
      this.currentStepIndex.update(i => i - 1);
    }
  }

  goToStep(index: number): void {
    // Optionnel : permettre de revenir en arrière ou d'aller aux étapes déjà validées
    if (index < this.currentStepIndex()) {
      this.currentStepIndex.set(index);
    } else if (index > this.currentStepIndex()) {
      // Pour aller en avant, il faut valider les étapes intermédiaires
      let canGo = true;
      for (let i = this.currentStepIndex(); i < index; i++) {
        const step = this.steps()[i];
        // Si c'est l'étape courante, on utilise effectiveForm() pour avoir la logique du contenu
        const validator = i === this.currentStepIndex() ? this.effectiveForm() : step;

        if (validator && !validator.isValid()) {
          validator.markTouched();
          canGo = false;
          break;
        }
      }
      if (canGo) {
        this.currentStepIndex.set(index);
      }
    }
  }
}
