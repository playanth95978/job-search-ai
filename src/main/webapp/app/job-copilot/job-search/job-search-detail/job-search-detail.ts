import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslatePipe } from '@ngx-translate/core';
import TranslateDirective from '../../../shared/language/translate.directive';
import { jobSourceColor, jobSourceLabel } from '../../shared/job-copilot.model';
import type { OfferDetail } from '../job-search.component';

/** Presentational side pane showing a single offer's detail (Seek-style). Owns no state. */
@Component({
  selector: 'jhi-job-search-detail',
  imports: [DecimalPipe, FaIconComponent, TranslateDirective, TranslatePipe],
  templateUrl: './job-search-detail.html',
  styleUrl: './job-search-detail.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class JobSearchDetail {
  /** The offer to display. */
  sel = input.required<OfferDetail>();
  /** True while the parent is fetching the full description. */
  readonly selectedLoading = input(false);
  /** Ask the parent to close the pane. */
  @Output() close = new EventEmitter<void>();
  /** Ask the parent to open the apply modal for this offer. */
  @Output() apply = new EventEmitter<OfferDetail>();

  closeDetail(): void {
    this.close.emit();
  }

  openApplyModal(offer: OfferDetail): void {
    this.apply.emit(offer);
  }

  getSourceColor(source?: string): string {
    return jobSourceColor(source);
  }

  getSourceLabel(source?: string): string {
    return jobSourceLabel(source);
  }
}
