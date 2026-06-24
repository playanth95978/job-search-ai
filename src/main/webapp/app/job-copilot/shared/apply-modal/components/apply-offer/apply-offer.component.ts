import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SafeHtml } from '@angular/platform-browser';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslatePipe } from '@ngx-translate/core';
import { JobOffer, JobOfferPreview } from '../../job-copilot.model';
import { SanitizerService } from '../../../../shared/sanitizer/sanitizer.service';
import TranslateDirective from '../../../../shared/language/translate.directive';

@Component({
  selector: 'jhi-apply-offer',
  standalone: true,
  imports: [CommonModule, FaIconComponent, TranslatePipe, TranslateDirective],
  templateUrl: './apply-offer.component.html',
  styleUrl: './apply-offer.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ApplyOfferComponent {
  offer = input<JobOfferPreview | JobOffer | null>(null);
  enriching = input(false);

  private sanitizer = inject(SanitizerService);

  descriptionHtml = computed<SafeHtml | null>(() => {
    const description = this.offer()?.description;
    return description ? this.sanitizer.sanitizeHtml(description) : null;
  });
}
