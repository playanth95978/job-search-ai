import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { AccountService } from 'app/core/auth/account.service';
import { SeoService } from 'app/pages/shared/seo.service';
import { JobCopilotService } from '../shared/job-copilot.service';
import { ApplyModalComponent } from '../shared/apply-modal/apply-modal.component';
import { AuthPromptModalComponent } from '../shared/auth-prompt-modal/auth-prompt-modal.component';
import {
  ADZUNA_COUNTRIES,
  JobOffer,
  JobOfferPreview,
  jobSourceColor,
  jobSourceLabel,
  MatchResult,
  SEARCHABLE_JOB_SOURCES,
} from '../shared/job-copilot.model';
import TranslateDirective from '../../shared/language/translate.directive';
import { JobSearchDetail } from './job-search-detail/job-search-detail';

/** Shared shape of an offer shown in the detail pane — satisfied by both JobOffer and JobOfferDto. */
export type OfferDetail = JobOfferPreview & {
  id?: string;
  remote?: boolean;
  contractType?: string;
  salaryMin?: number;
  salaryMax?: number;
};

@Component({
  selector: 'jhi-job-copilot-search',
  standalone: true,
  imports: [CommonModule, FormsModule, FaIconComponent, TranslateModule, TranslateDirective, JobSearchDetail],
  templateUrl: './job-search.component.html',
  styleUrl: './job-search.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class JobSearchComponent {
  keywords = '';
  location = '';
  source = '';
  /** Adzuna market (country path segment); only used when the Adzuna source is selected. */
  country = 'fr';
  atsUrl = '';
  /** Number of semantically closest offers to score in "Match My Profile". */
  matchTopK = 30;

  readonly sources = SEARCHABLE_JOB_SOURCES;
  readonly countries = ADZUNA_COUNTRIES;

  /** The metadata of the currently selected source, or undefined for "all sources". */
  get selectedSource() {
    return this.sources.find(s => s.value === this.source);
  }

  offers = signal<JobOffer[]>([]);
  matchResults = signal<MatchResult[]>([]);
  /** Seek-style detail pane: the offer currently opened alongside the results list. */
  selectedOffer = signal<OfferDetail | null>(null);
  selectedLoading = signal(false);
  searching = signal(false);
  smartSearching = signal(false);
  matching = signal(false);
  matchingAdvanced = signal(false);
  fetching = signal(false);
  error = signal<string | null>(null);
  activeTab = signal<'search' | 'ats'>('search');

  private service = inject(JobCopilotService);
  private translateService = inject(TranslateService);
  private modalService = inject(NgbModal);
  private accountService = inject(AccountService);
  private seo = inject(SeoService);

  constructor() {
    // This component backs both the public /search route (crawlable) and the auth-gated
    // /job-copilot/search route. Pin the canonical to the public /search URL so search
    // engines index a single, reachable page and never a login redirect.
    this.seo.update({
      title: "Recherche d'emploi IA — Trouvez les offres qui matchent votre profil | Job Search AI",
      description:
        "Recherchez des offres d'emploi et laissez l'IA les faire correspondre à votre CV. Matching sémantique, multi-sources, candidature assistée.",
      path: '/search',
    });
  }

  search(): void {
    this.searching.set(true);
    this.matchResults.set([]);
    this.error.set(null);
    const country = this.selectedSource?.hasCountrySelect ? this.country : undefined;
    this.service.searchJobs(this.keywords || undefined, this.location || undefined, this.source || undefined, country).subscribe({
      next: o => {
        this.offers.set(o);
        this.searching.set(false);
      },
      error: err => {
        this.error.set(err.error?.message || 'Search failed');
        this.searching.set(false);
      },
    });
  }

  /**
   * Semantic search over the ingested corpus (hybrid vector + BM25 + RRF + reranking).
   * Instant and meaning-aware, vs. the live board scraping of {@link search}.
   */
  smartSearch(): void {
    if (!this.keywords) {
      this.search();
      return;
    }
    this.smartSearching.set(true);
    this.matchResults.set([]);
    this.error.set(null);
    this.service.searchSmart(this.keywords, this.location || undefined, this.source || undefined).subscribe({
      next: o => {
        this.offers.set(o);
        this.smartSearching.set(false);
      },
      error: err => {
        this.error.set(err.error?.message || 'Search failed');
        this.smartSearching.set(false);
      },
    });
  }

  fetchUrl(): void {
    if (!this.atsUrl) return;
    this.fetching.set(true);
    this.error.set(null);
    this.service.fetchFromUrl(this.atsUrl).subscribe({
      next: o => {
        this.offers.set(o);
        this.fetching.set(false);
      },
      error: err => {
        this.error.set(err.error?.message || 'Fetch failed');
        this.fetching.set(false);
      },
    });
  }

  matchAll(): void {
    if (!this.requireAuth('jobCopilot.authPrompt.feature.matchProfile')) return;
    this.matching.set(true);
    this.error.set(null);
    this.service.matchJobs(this.matchTopK, this.location || undefined).subscribe({
      next: r => {
        this.matchResults.set(r);
        this.matching.set(false);
      },
      error: err => {
        this.error.set(err.error?.message || 'Matching failed');
        this.matching.set(false);
      },
    });
  }

  matchAdvanced(): void {
    if (!this.requireAuth('jobCopilot.authPrompt.feature.matchAdvanced')) return;
    this.matchingAdvanced.set(true);
    this.service.matchAdvanced(this.location || undefined).subscribe({
      next: r => {
        this.matchResults.set(r);
        this.matchingAdvanced.set(false);
      },
      error: () => this.matchingAdvanced.set(false),
    });
  }

  /**
   * Opens an offer in the side detail pane (Seek-style). Persisted teaser (STUB) offers only carry a
   * short description in the list, so we lazily fetch the full one and patch both the pane and the row.
   */
  selectOffer(offer: OfferDetail): void {
    this.selectedOffer.set(offer);
    if (offer.id && (!offer.description || offer.description.length < 200)) {
      this.selectedLoading.set(true);
      this.service.enrichJob(offer.id).subscribe({
        next: full => {
          this.selectedOffer.update(cur => (cur && cur.id === offer.id ? { ...cur, ...full } : cur));
          this.offers.update(list => list.map(o => (o.id === offer.id ? { ...o, ...full } : o)));
          this.selectedLoading.set(false);
        },
        error: () => this.selectedLoading.set(false),
      });
    }
  }

  openApplyModal(offer: JobOfferPreview & { id?: string }): void {
    if (!this.requireAuth('jobCopilot.authPrompt.feature.apply')) return;
    if (offer.id) {
      this.openModal(offer.id, offer);
      return;
    }

    // Offer comes from a match/preview without a persisted id: resolve it via the
    // JHipster criteria filters (applyUrl is a StringFilter) to recover its id.
    if (!offer.applyUrl) {
      this.error.set('Cannot open application: offer has no identifier.');
      return;
    }

    this.service.query({ 'applyUrl.equals': offer.applyUrl, size: 1 }).subscribe({
      next: res => {
        const found = res.body?.[0];
        if (found?.id) {
          this.openModal(found.id, { ...offer, ...found });
        } else {
          this.error.set('Offer not found.');
        }
      },
      error: err => this.error.set(err.error?.message || 'Failed to load the offer'),
    });
  }

  private openModal(offerId: string, offer: JobOfferPreview): void {
    const modalRef = this.modalService.open(ApplyModalComponent, { size: 'xl', backdrop: 'static', scrollable: true });
    modalRef.componentInstance.jobOfferId.set(offerId);
    modalRef.componentInstance.jobOffer.set(offer);

    // The modal owns the lazy enrichment; patch the list row with the offer it fetched.
    modalRef.componentInstance.enrichedOffer.subscribe((enriched: JobOffer) => {
      this.offers.update(list => list.map(o => (o.id === offerId ? { ...o, ...enriched } : o)));
    });
  }

  /**
   * Gate for auth-only actions. Anonymous visitors get an invitation modal (register / login)
   * instead of a request that would 401; returns false so the caller aborts.
   */
  private requireAuth(featureKey: string): boolean {
    if (this.accountService.isAuthenticated()) return true;
    const modalRef = this.modalService.open(AuthPromptModalComponent, { centered: true });
    modalRef.componentInstance.featureKey.set(featureKey);
    return false;
  }

  getScoreClass(score: number): string {
    if (score >= 0.7) return 'high';
    if (score >= 0.4) return 'medium';
    return 'low';
  }

  getSourceLabel(source?: string): string {
    return jobSourceLabel(source);
  }

  getSourceColor(source?: string): string {
    return jobSourceColor(source);
  }
}
