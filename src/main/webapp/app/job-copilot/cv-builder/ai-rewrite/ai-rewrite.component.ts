import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateModule } from '@ngx-translate/core';
import TranslateDirective from 'app/shared/language/translate.directive';

import { CvResumeApiService, RewriteKind, RewriteResponse } from '../cv-resume-api.service';

interface RewriteVariant {
  key: keyof RewriteResponse;
  labelKey: string;
  text: string;
}

/**
 * "Improve with AI" control for a single free-text field. Generates 3 variants
 * (professional / concise / ATS) and emits the one the user applies. Generic: the parent
 * decides where to write the chosen text via {@link apply}.
 */
@Component({
  selector: 'jhi-ai-rewrite',
  standalone: true,
  imports: [CommonModule, FaIconComponent, TranslateModule, TranslateDirective],
  templateUrl: './ai-rewrite.component.html',
  styleUrl: './ai-rewrite.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AiRewriteComponent {
  private readonly api = inject(CvResumeApiService);

  readonly text = input.required<string>();
  readonly kind = input.required<RewriteKind>();
  readonly jobTitle = input<string>('');
  readonly company = input<string>('');

  readonly apply = output<string>();

  readonly open = signal(false);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  private readonly result = signal<RewriteResponse | null>(null);

  readonly disabled = computed(() => !this.text().trim());

  readonly variants = computed<RewriteVariant[]>(() => {
    const r = this.result();
    if (!r) return [];
    const list: RewriteVariant[] = [
      { key: 'professional', labelKey: 'cvBuilder.rewrite.variants.professional', text: r.professional },
      { key: 'concise', labelKey: 'cvBuilder.rewrite.variants.concise', text: r.concise },
      { key: 'ats', labelKey: 'cvBuilder.rewrite.variants.ats', text: r.ats },
    ];
    return list.filter(v => v.text?.trim());
  });

  run(): void {
    if (this.disabled() || this.loading()) return;
    this.open.set(true);
    this.loading.set(true);
    this.error.set(null);
    this.result.set(null);
    this.api
      .rewrite({ text: this.text(), kind: this.kind(), jobTitle: this.jobTitle() || undefined, company: this.company() || undefined })
      .subscribe({
        next: r => {
          this.result.set(r);
          this.loading.set(false);
        },
        error: err => {
          this.error.set(err.error?.message ?? 'AI rewrite failed');
          this.loading.set(false);
        },
      });
  }

  choose(variant: RewriteVariant): void {
    this.apply.emit(variant.text);
    this.close();
  }

  close(): void {
    this.open.set(false);
    this.result.set(null);
    this.error.set(null);
  }
}
