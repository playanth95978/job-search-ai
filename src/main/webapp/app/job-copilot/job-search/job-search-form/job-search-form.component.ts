import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateModule } from '@ngx-translate/core';

import TranslateDirective from '../../../shared/language/translate.directive';
import { ADZUNA_COUNTRIES, SEARCHABLE_JOB_SOURCES } from '../../shared/job-copilot.model';
import { JobSearchCriteria } from '../job-search.types';

/**
 * Search controls for the job-search page: keyword/ATS tabs, filters and action buttons. Signal-first
 * and self-contained — it owns the form fields as signals and emits typed criteria on each action,
 * so the parent stays free of form state. Loading flags are inputs that drive the button spinners.
 */
@Component({
  selector: 'jhi-job-search-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, FaIconComponent, TranslateModule, TranslateDirective],
  templateUrl: './job-search-form.component.html',
  styleUrl: './job-search-form.component.scss',
})
export class JobSearchFormComponent {
  // Loading flags (drive spinners / disabled state).
  readonly searching = input(false);
  readonly smartSearching = input(false);
  readonly matching = input(false);
  readonly matchingAdvanced = input(false);
  readonly fetching = input(false);

  readonly search = output<JobSearchCriteria>();
  readonly smartSearch = output<JobSearchCriteria>();
  readonly matchAll = output<JobSearchCriteria>();
  readonly matchAdvanced = output<JobSearchCriteria>();
  readonly fetchUrl = output<string>();

  readonly sources = SEARCHABLE_JOB_SOURCES;
  readonly countries = ADZUNA_COUNTRIES;

  readonly activeTab = signal<'search' | 'ats'>('search');
  readonly keywords = signal('');
  readonly location = signal('');
  readonly source = signal('');
  /** Adzuna market (country path segment); only used when the Adzuna source is selected. */
  readonly country = signal('fr');
  readonly atsUrl = signal('');

  readonly selectedSource = computed(() => this.sources.find(s => s.value === this.source()));

  private criteria(): JobSearchCriteria {
    return {
      keywords: this.keywords() || undefined,
      location: this.location() || undefined,
      source: this.source() || undefined,
      country: this.selectedSource()?.hasCountrySelect ? this.country() : undefined,
    };
  }

  onSearch(): void {
    this.search.emit(this.criteria());
  }

  onSmartSearch(): void {
    this.smartSearch.emit(this.criteria());
  }

  onMatchAll(): void {
    this.matchAll.emit(this.criteria());
  }

  onMatchAdvanced(): void {
    this.matchAdvanced.emit(this.criteria());
  }

  onFetch(): void {
    const url = this.atsUrl().trim();
    if (url) this.fetchUrl.emit(url);
  }
}
