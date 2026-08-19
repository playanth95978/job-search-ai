import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateModule } from '@ngx-translate/core';

import TranslateDirective from '../../../shared/language/translate.directive';
import { buildFacet, FilterableOffer, hasActiveFilters, NO_FILTERS, offerCategory } from '../job-search-filtering';
import { DatePostedKey, JobSearchFilters, JobSortKey, OfferFacet } from '../job-search.types';

/**
 * Left-hand filter panel of the search page. Presentational: it owns the filter state and emits it,
 * while the parent keeps the offers and applies the filtering.
 *
 * <p>Facet lists (location, category, company, contract type) are derived from the results currently
 * loaded, so every value shown has at least one match. A fixed vocabulary would offer options that
 * return nothing, since the sources have no shared taxonomy.
 */
@Component({
  selector: 'jhi-job-search-filters',
  standalone: true,
  imports: [DecimalPipe, FormsModule, FaIconComponent, TranslateModule, TranslateDirective],
  templateUrl: './job-search-filters.component.html',
  styleUrl: './job-search-filters.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class JobSearchFiltersComponent {
  /** The unfiltered result set the facets are computed from. */
  readonly offers = input.required<FilterableOffer[]>();
  /** How many offers survive the current filters, shown as the panel's result count. */
  readonly visibleCount = input<number>(0);

  readonly filtersChange = output<JobSearchFilters>();

  readonly filters = signal<JobSearchFilters>({ ...NO_FILTERS });

  /** Collapsed by default on mobile, where a permanent sidebar would push the results off-screen. */
  readonly expanded = signal(false);

  readonly sortOptions: JobSortKey[] = ['relevance', 'date', 'salary'];
  readonly datePostedOptions: DatePostedKey[] = ['any', '24h', '7d', '30d'];
  /** Salary floors in local currency; null is the "no minimum" entry. */
  readonly salarySteps: (number | null)[] = [null, 30000, 50000, 80000, 120000];

  readonly locationFacets = computed(() => buildFacet(this.offers(), o => o.location));
  readonly companyFacets = computed(() => buildFacet(this.offers(), o => o.company));
  readonly contractTypeFacets = computed(() => buildFacet(this.offers(), o => o.contractType));
  readonly categoryFacets = computed(() => buildFacet(this.offers(), offerCategory));

  readonly anyActive = computed(() => hasActiveFilters(this.filters()));

  setSort(sort: JobSortKey): void {
    this.patch({ sort });
  }

  setDatePosted(datePosted: DatePostedKey): void {
    this.patch({ datePosted });
  }

  setMinSalary(minSalary: number | null): void {
    this.patch({ minSalary });
  }

  toggleRemote(): void {
    this.patch({ remoteOnly: !this.filters().remoteOnly });
  }

  /** Multi-select facets are additive: ticking two companies shows offers from either. */
  toggleFacet(key: 'locations' | 'categories' | 'companies' | 'contractTypes', value: string): void {
    const current = this.filters()[key];
    const next = current.includes(value) ? current.filter(v => v !== value) : [...current, value];
    this.patch({ [key]: next } as Partial<JobSearchFilters>);
  }

  isChecked(key: 'locations' | 'categories' | 'companies' | 'contractTypes', value: string): boolean {
    return this.filters()[key].includes(value);
  }

  clearAll(): void {
    // The sort is a display preference, not a filter, so "clear" leaves it alone.
    this.patch({ ...NO_FILTERS, sort: this.filters().sort });
  }

  /** Category facet values are ids; their labels live under jobCopilot.search.filters.categories.*. */
  categoryLabelKey(facet: OfferFacet): string {
    return `jobCopilot.search.filters.categories.${facet.value}`;
  }

  private patch(change: Partial<JobSearchFilters>): void {
    this.filters.update(current => ({ ...current, ...change }));
    this.filtersChange.emit(this.filters());
  }
}
