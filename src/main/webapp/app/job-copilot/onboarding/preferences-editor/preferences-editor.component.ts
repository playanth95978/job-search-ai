import { ChangeDetectionStrategy, Component, computed, model } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateModule } from '@ngx-translate/core';
import { TranslateDirective } from 'app/shared/language';

import { UserPreferenceDto } from '../../shared/job-copilot.model';
import { ChipsInputComponent } from '../../shared/chips-input/chips-input.component';

/** Job-preference contract types offered in the onboarding preferences step. */
const CONTRACT_TYPES = ['CDI', 'CDD', 'FREELANCE', 'INTERNSHIP', 'APPRENTICESHIP', 'TEMPORARY'] as const;

/**
 * Editable job-search preferences. Signal-first: preferences are a two-way {@link model} updated
 * immutably. Target roles are intentionally not edited here — they are kept in sync with the
 * profile's roles by the container.
 */
@Component({
  selector: 'jhi-preferences-editor',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, FaIconComponent, TranslateModule, TranslateDirective, ChipsInputComponent],
  templateUrl: './preferences-editor.component.html',
  styleUrl: './preferences-editor.component.scss',
})
export class PreferencesEditorComponent {
  readonly preferences = model.required<UserPreferenceDto>();
  readonly contractTypes = CONTRACT_TYPES;

  readonly locations = computed(() => this.preferences().preferredLocations ?? []);
  readonly excluded = computed(() => this.preferences().excludedTechnologies ?? []);

  patch(partial: Partial<UserPreferenceDto>): void {
    this.preferences.update(p => ({ ...p, ...partial }));
  }
}
