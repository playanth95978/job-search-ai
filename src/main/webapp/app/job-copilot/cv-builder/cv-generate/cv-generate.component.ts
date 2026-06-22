import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateModule } from '@ngx-translate/core';
import TranslateDirective from 'app/shared/language/translate.directive';

import { ResumeStore } from '../resume-store';
import { CvResumeApiService } from '../cv-resume-api.service';
import { generatedResumeToResume } from '../generated-resume.mapper';

/**
 * Full AI resume generation: the user pastes free-form source text (an old CV, LinkedIn export,
 * raw notes…) and an optional target offer; the LLM returns a complete structured resume that
 * auto-fills the builder. Applied via {@link ResumeStore.mutate} so it stays undoable.
 */
@Component({
  selector: 'jhi-cv-generate',
  imports: [CommonModule, FormsModule, FaIconComponent, TranslateModule, TranslateDirective],
  templateUrl: './cv-generate.component.html',
  styleUrl: '../cv-match/cv-match.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CvGenerateComponent {
  private readonly api = inject(CvResumeApiService);
  private readonly store = inject(ResumeStore);

  readonly open = signal(false);
  readonly source = signal('');
  readonly offer = signal('');
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  run(): void {
    const source = this.source().trim();
    if (!source || this.loading()) return;
    this.loading.set(true);
    this.error.set(null);
    this.api.generateResume(source, this.offer().trim()).subscribe({
      next: dto => {
        this.store.mutate(r => generatedResumeToResume(dto, r));
        this.loading.set(false);
        this.open.set(false);
        this.source.set('');
        this.offer.set('');
      },
      error: err => {
        this.error.set(err.error?.message ?? 'AI generation failed');
        this.loading.set(false);
      },
    });
  }
}
