import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateModule } from '@ngx-translate/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';

import { AccountService } from 'app/core/auth/account.service';
import { SeoService } from 'app/pages/shared/seo.service';
import { JobCopilotService } from '../shared/job-copilot.service';
import { ApplyModalComponent } from '../shared/apply-modal/apply-modal.component';
import { AuthPromptModalComponent } from '../shared/auth-prompt-modal/auth-prompt-modal.component';
import { JobOffer, JobOfferPreview, MatchResult, BoardSuggestion } from '../shared/job-copilot.model';
import TranslateDirective from '../../shared/language/translate.directive';
import { JobSearchDetail } from './job-search-detail/job-search-detail';
import { JobSearchFormComponent } from './job-search-form/job-search-form.component';
import { JobResultsComponent } from './job-results/job-results.component';
import { JobSearchCriteria, OfferDetail } from './job-search.types';

// Re-exported so existing importers (e.g. the detail pane) keep resolving OfferDetail from here.
export type { OfferDetail } from './job-search.types';

/**
 * Job-search page orchestrator. Owns the results/selection state and the service calls, and composes
 * the standalone {@link JobSearchFormComponent} (controls), {@link JobResultsComponent} (list) and
 * {@link JobSearchDetail} (side pane). Form state lives in the form; this component only reacts to
 * the criteria it emits.
 */
@Component({
  selector: 'jhi-job-copilot-search',
  standalone: true,
  imports: [FaIconComponent, TranslateModule, TranslateDirective, JobSearchFormComponent, JobResultsComponent, JobSearchDetail],
  templateUrl: './job-search.component.html',
  styleUrl: './job-search.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class JobSearchComponent {
  /** Number of semantically closest offers to score in "Match My Profile". */
  private static readonly MATCH_TOP_K = 30;

  readonly offers = signal<JobOffer[]>([]);
  readonly matchResults = signal<MatchResult[]>([]);
  /** Seek-style detail pane: the offer currently opened alongside the results list. */
  readonly selectedOffer = signal<OfferDetail | null>(null);
  readonly selectedLoading = signal(false);

  readonly searching = signal(false);
  readonly smartSearching = signal(false);
  readonly matching = signal(false);
  readonly matchingAdvanced = signal(false);
  readonly fetching = signal(false);
  readonly suggestingBoards = signal(false);
  readonly translating = signal(false);
  /** Langue actuellement affichée dans le volet de détail ; null = texte d'origine. */
  readonly translatedLanguage = signal<string | null>(null);
  readonly boardSuggestions = signal<BoardSuggestion[]>([]);
  /** Distingue « suggestion jamais lancée » de « lancée, aucun board trouvé ». */
  readonly boardsSuggested = signal(false);
  readonly error = signal<string | null>(null);

  /** Offre telle que reçue de la source, gardée pour pouvoir annuler une traduction. */
  private originalOffer: OfferDetail | null = null;

  private readonly service = inject(JobCopilotService);
  private readonly modalService = inject(NgbModal);
  private readonly accountService = inject(AccountService);
  private readonly seo = inject(SeoService);

  constructor() {
    // Backs both the public /search route (crawlable) and the auth-gated /job-copilot/search route.
    // Pin the canonical to the public /search URL so search engines index a single, reachable page.
    this.seo.update({
      title: "Recherche d'emploi IA — Trouvez les offres qui matchent votre profil | Job Search AI",
      description:
        "Recherchez des offres d'emploi et laissez l'IA les faire correspondre à votre CV. Matching sémantique, multi-sources, candidature assistée.",
      path: '/search',
    });
  }

  onSearch(c: JobSearchCriteria): void {
    this.searching.set(true);
    this.matchResults.set([]);
    this.error.set(null);
    this.service.searchJobs(c.keywords, c.location, c.source, c.country).subscribe({
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

  /** Semantic search over the ingested corpus (hybrid vector + BM25 + RRF + reranking). */
  onSmartSearch(c: JobSearchCriteria): void {
    if (!c.keywords) {
      this.onSearch(c);
      return;
    }
    this.smartSearching.set(true);
    this.matchResults.set([]);
    this.error.set(null);
    this.service.searchSmart(c.keywords, c.location, c.source).subscribe({
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

  onFetch(url: string): void {
    this.fetching.set(true);
    this.error.set(null);
    this.service.fetchFromUrl(url).subscribe({
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

  /**
   * Suggère des boards ATS à partir du profil. Réservé aux utilisateurs connectés : la suggestion
   * part de leur CV.
   */
  onSuggestBoards(): void {
    if (!this.requireAuth('jobCopilot.authPrompt.feature.matchProfile')) return;
    this.suggestingBoards.set(true);
    this.error.set(null);
    this.service.suggestBoards().subscribe({
      next: boards => {
        this.boardSuggestions.set(boards);
        this.boardsSuggested.set(true);
        this.suggestingBoards.set(false);
      },
      error: err => {
        this.error.set(err.error?.message ?? 'Board suggestion failed');
        this.suggestingBoards.set(false);
      },
    });
  }

  onMatchAll(c: JobSearchCriteria): void {
    if (!this.requireAuth('jobCopilot.authPrompt.feature.matchProfile')) return;
    this.matching.set(true);
    this.error.set(null);
    this.service.matchJobs(JobSearchComponent.MATCH_TOP_K, c.location).subscribe({
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

  onMatchAdvanced(c: JobSearchCriteria): void {
    if (!this.requireAuth('jobCopilot.authPrompt.feature.matchAdvanced')) return;
    this.matchingAdvanced.set(true);
    this.service.matchAdvanced(c.location).subscribe({
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
    // Une nouvelle offre repart toujours de sa langue d'origine.
    this.originalOffer = null;
    this.translatedLanguage.set(null);
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

  /**
   * Traduit l'offre affichée. Le texte d'origine est conservé à part : la traduction est une vue,
   * pas une modification — l'utilisateur doit pouvoir revenir à la version publiée par la source.
   */
  onTranslate(language: string): void {
    const offer = this.selectedOffer();
    if (!offer?.description) return;
    this.originalOffer ??= offer;
    this.translating.set(true);
    this.error.set(null);
    this.service.translateJob(this.originalOffer, language).subscribe({
      next: translated => {
        this.selectedOffer.update(cur => (cur ? { ...cur, title: translated.title, description: translated.description } : cur));
        this.translatedLanguage.set(language);
        this.translating.set(false);
      },
      error: err => {
        this.error.set(err.error?.message ?? 'Translation failed');
        this.translating.set(false);
      },
    });
  }

  /** Rétablit le texte publié par la source. */
  onRestoreOriginal(): void {
    if (this.originalOffer) {
      this.selectedOffer.set(this.originalOffer);
    }
    this.translatedLanguage.set(null);
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
}
