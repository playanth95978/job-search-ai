import { ChangeDetectionStrategy, Component, forwardRef, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

/**
 * Reusable tag/chip input bound to a `string[]` via ControlValueAccessor.
 * Used for roles, locations and excluded technologies — one component, zero duplicated CSS.
 */
@Component({
  selector: 'jhi-chip-input',
  standalone: true,
  imports: [FaIconComponent],
  templateUrl: './chip-input.component.html',
  styleUrl: './chip-input.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => ChipInputComponent), multi: true }],
})
export class ChipInputComponent implements ControlValueAccessor {
  readonly chips = signal<string[]>([]);
  readonly disabled = signal(false);

  private onChange: (value: string[]) => void = () => {};
  private onTouched: () => void = () => {};

  writeValue(value: string[] | null | undefined): void {
    this.chips.set(value ?? []);
  }

  registerOnChange(fn: (value: string[]) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled.set(isDisabled);
  }

  /** Adds the typed value(s) (Enter or comma) as chip(s), ignoring blanks/duplicates. */
  add(event: Event, input: HTMLInputElement): void {
    event.preventDefault();
    const parts = input.value
      .split(',')
      .map(p => p.trim())
      .filter(Boolean);
    if (!parts.length) return;
    const next = [...this.chips()];
    for (const p of parts) {
      if (!next.some(c => c.toLowerCase() === p.toLowerCase())) {
        next.push(p);
      }
    }
    this.chips.set(next);
    input.value = '';
    this.emit();
  }

  remove(chip: string): void {
    this.chips.set(this.chips().filter(c => c !== chip));
    this.emit();
  }

  private emit(): void {
    this.onChange(this.chips());
    this.onTouched();
  }
}
