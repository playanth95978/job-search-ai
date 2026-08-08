import { ChangeDetectionStrategy, Component, input, model, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

/**
 * Reusable tag/chip editor over a {@code string[]}. Fully signal-based: the list is a two-way
 * {@link model} and the pending text is a local {@link signal}. Duplicates and blanks are ignored.
 * Reused for skills, target roles, preferred locations and excluded technologies.
 */
@Component({
  selector: 'jhi-chips-input',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, FaIconComponent],
  templateUrl: './chips-input.component.html',
  styleUrl: './chips-input.component.scss',
})
export class ChipsInputComponent {
  /** The chips, two-way bound: {@code [(values)]} or {@code [values]}/{@code (valuesChange)}. */
  readonly values = model<string[]>([]);
  readonly placeholder = input<string>('');
  /** Red styling for "negative" chips (e.g. excluded technologies). */
  readonly danger = input<boolean>(false);

  /** Text currently being typed, before it is committed as a chip. */
  readonly draft = signal('');

  add(): void {
    const value = this.draft().trim();
    this.draft.set('');
    if (!value || this.values().includes(value)) return;
    this.values.update(list => [...list, value]);
  }

  remove(index: number): void {
    this.values.update(list => list.filter((_, i) => i !== index));
  }
}
