import { ChangeDetectionStrategy, Component, EventEmitter, Output, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslatePipe } from '@ngx-translate/core';
import { NgbDropdown, NgbDropdownItem, NgbDropdownMenu, NgbDropdownToggle } from '@ng-bootstrap/ng-bootstrap';
import TranslateDirective from '../../../shared/language/translate.directive';
import { TRANSLATION_LANGUAGES, jobSourceColor, jobSourceLabel } from '../../shared/job-copilot.model';
import type { OfferDetail } from '../job-search.component';

/** Presentational side pane showing a single offer's detail (Seek-style). Owns no state. */
@Component({
  selector: 'jhi-job-search-detail',
  imports: [
    DecimalPipe,
    FaIconComponent,
    TranslateDirective,
    TranslatePipe,
    NgbDropdown,
    NgbDropdownToggle,
    NgbDropdownMenu,
    NgbDropdownItem,
  ],
  templateUrl: './job-search-detail.html',
  styleUrl: './job-search-detail.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class JobSearchDetail {
  /** The offer to display. */
  sel = input.required<OfferDetail>();
  /** True while the parent is fetching the full description. */
  readonly selectedLoading = input(false);
  /** True while the parent is translating the description. */
  readonly translating = input(false);
  /** Language currently displayed, or null when the original text is shown. */
  readonly translatedLanguage = input<string | null>(null);
  /** Ask the parent to close the pane. */
  @Output() close = new EventEmitter<void>();
  /** Ask the parent to open the apply modal for this offer. */
  @Output() apply = new EventEmitter<OfferDetail>();
  /** Ask the parent to translate the offer into the given language. */
  @Output() translateRequested = new EventEmitter<string>();
  /** Ask the parent to restore the original text. */
  @Output() restoreOriginal = new EventEmitter<void>();

  readonly languages = TRANSLATION_LANGUAGES;

  /** Libellé natif de la langue affichée, pour l'étiquette du bouton. */
  currentLanguageLabel(): string | undefined {
    return this.languages.find(language => language.value === this.translatedLanguage())?.label;
  }

  closeDetail(): void {
    this.close.emit();
  }

  openApplyModal(offer: OfferDetail): void {
    this.apply.emit(offer);
  }

  onTranslate(language: string): void {
    this.translateRequested.emit(language);
  }

  onRestoreOriginal(): void {
    this.restoreOriginal.emit();
  }

  getSourceColor(source?: string): string {
    return jobSourceColor(source);
  }

  getSourceLabel(source?: string): string {
    return jobSourceLabel(source);
  }
}
