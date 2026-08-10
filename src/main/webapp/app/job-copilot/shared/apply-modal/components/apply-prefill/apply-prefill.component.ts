import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslatePipe } from '@ngx-translate/core';
import TranslateDirective from '../../../../../shared/language/translate.directive';

export interface PrefillField {
  key: string;
  labelKey: string;
  value: string | undefined | null;
}

@Component({
  selector: 'jhi-apply-prefill',
  standalone: true,
  imports: [CommonModule, FaIconComponent, TranslatePipe, TranslateDirective],
  templateUrl: './apply-prefill.component.html',
  styleUrl: './apply-prefill.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ApplyPrefillComponent {
  candidateProfile = input<any>();
  coverLetter = input<string | null>(null);

  /** Which single prefill field was last copied, to flash a per-field "copied" check. */
  copiedField = signal<string | null>(null);

  fields = computed<PrefillField[]>(() => {
    const c = this.candidateProfile();
    return [
      { key: 'fullName', labelKey: 'jobCopilot.applyModal.prefill.fullName', value: c?.fullName },
      { key: 'email', labelKey: 'jobCopilot.applyModal.prefill.email', value: c?.email },
      { key: 'location', labelKey: 'jobCopilot.applyModal.prefill.location', value: c?.location },
      { key: 'coverLetter', labelKey: 'jobCopilot.applyModal.prefill.coverLetter', value: this.coverLetter() },
    ];
  });

  copyField(field: string, value?: string | null): void {
    if (!value) return;
    void navigator.clipboard.writeText(value).then(() => {
      this.copiedField.set(field);
      setTimeout(() => this.copiedField.set(null), 1500);
    });
  }
}
