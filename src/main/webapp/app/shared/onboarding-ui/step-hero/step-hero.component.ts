import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

/**
 * Animated header reused by every onboarding step: a gradient icon pill, a title and a subtitle.
 * Centralises the step heading so no step duplicates the markup or CSS.
 */
@Component({
  selector: 'jhi-step-hero',
  standalone: true,
  imports: [FaIconComponent],
  templateUrl: './step-hero.component.html',
  styleUrl: './step-hero.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StepHeroComponent {
  readonly icon = input.required<string>();
  readonly title = input.required<string>();
  readonly subtitle = input<string>('');
}
