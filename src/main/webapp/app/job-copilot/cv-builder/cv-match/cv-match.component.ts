import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateModule } from '@ngx-translate/core';
import TranslateDirective from 'app/shared/language/translate.directive';

import { Resume, resumeToPlainText } from '../resume.model';
import { CvMatchResult, CvResumeApiService } from '../cv-resume-api.service';
import { MatchResultComponent } from '../../shared/match-result/match-result.component';

/** Match the current resume against a pasted job offer: global + sub-scores, gaps, suggestions. */
@Component({
  selector: 'jhi-cv-match',
  imports: [CommonModule, FormsModule, FaIconComponent, TranslateModule, TranslateDirective, MatchResultComponent],
  templateUrl: './cv-match.component.html',
  styleUrl: './cv-match.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CvMatchComponent {
  private readonly api = inject(CvResumeApiService);

  readonly resume = input.required<Resume>();

  readonly open = signal(false);
  readonly offer = signal('');
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly result = signal<CvMatchResult | null>(null);

  run(): void {
    const offer = this.offer().trim();
    if (!offer || this.loading()) return;
    this.loading.set(true);
    this.error.set(null);
    this.api.matchResume(resumeToPlainText(this.resume()), offer).subscribe({
      next: r => {
        this.result.set(r);
        this.loading.set(false);
      },
      error: err => {
        this.error.set(err.error?.message ?? 'AI match failed');
        this.loading.set(false);
      },
    });
  }
}
