import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SafeHtml } from '@angular/platform-browser';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { JobOffer, JobOfferPreview } from '../../../job-copilot.model';
import { SanitizerService } from '../../../../../shared/sanitizer/sanitizer.service';
import TranslateDirective from '../../../../../shared/language/translate.directive';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { COVER_LETTER_LANGUAGES } from '../../apply-modal.component';
import { JobCopilotService } from '../../../job-copilot.service';

@Component({
  selector: 'jhi-apply-offer',
  standalone: true,
  imports: [CommonModule, FaIconComponent, TranslateDirective, ReactiveFormsModule, TranslatePipe, FormsModule],
  templateUrl: './apply-offer.component.html',
  styleUrl: './apply-offer.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ApplyOfferComponent {
  offer = input<JobOfferPreview | JobOffer | null | undefined>(null);
  enriching = input(false);
  language = signal('');
  readonly languages = COVER_LETTER_LANGUAGES;
  protected readonly jobOfferService = inject(JobCopilotService);

  translatedOffer = signal<JobOffer | null>(null);
  translating = signal(false);

  private sanitizer = inject(SanitizerService);

  constructor() {
    effect(
      () => {
        const lang = this.language();
        const offer = this.offer();
        if (lang && offer) {
          this.translating.set(true);
          this.jobOfferService.translateJob(offer as JobOffer, lang).subscribe({
            next: translated => {
              this.translatedOffer.set(translated);
              this.translating.set(false);
            },
            error: () => this.translating.set(false),
          });
        } else {
          this.translatedOffer.set(null);
        }
      },
      { allowSignalWrites: true },
    );
  }

  descriptionHtml = computed<SafeHtml | null>(() => {
    const description = this.translatedOffer()?.description ?? this.offer()?.description;
    return description ? this.sanitizer.sanitizeHtml(description) : null;
  });

  displayTitle = computed(() => this.translatedOffer()?.title ?? this.offer()?.title);
}
