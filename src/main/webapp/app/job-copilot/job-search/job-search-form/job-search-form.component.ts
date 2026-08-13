import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateModule } from '@ngx-translate/core';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';

import TranslateDirective from '../../../shared/language/translate.directive';
import { ADZUNA_COUNTRIES, SEARCHABLE_JOB_SOURCES } from '../../shared/job-copilot.model';
import { JobSearchCriteria } from '../job-search.types';
import { BoardSuggestion } from '../../shared/job-copilot.model';

/**
 * Search controls for the job-search page: keyword/ATS tabs, filters and action buttons. Signal-first
 * and self-contained — it owns the form fields as signals and emits typed criteria on each action,
 * so the parent stays free of form state. Loading flags are inputs that drive the button spinners.
 */
@Component({
  selector: 'jhi-job-search-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, FaIconComponent, TranslateModule, TranslateDirective, NgbTooltip],
  templateUrl: './job-search-form.component.html',
  styleUrl: './job-search-form.component.scss',
})
export class JobSearchFormComponent {
  /**
   * Panneau d'aide déplié. Double l'info-bulle plutôt que la remplacer : `ngbTooltip` réagit au
   * survol et au focus, absents sur écran tactile où un appui déclenche l'action. Le panneau donne
   * donc au doigt ce que l'info-bulle donne à la souris, à partir des mêmes textes.
   */
  readonly helpOpen = signal(false);

  // Loading flags (drive spinners / disabled state).
  readonly searching = input(false);
  readonly smartSearching = input(false);
  readonly matching = input(false);
  readonly matchingAdvanced = input(false);
  readonly fetching = input(false);
  readonly suggestingBoards = input(false);
  /** Boards résolus pour le profil ; vide tant que l'utilisateur n'a pas lancé la suggestion. */
  readonly boardSuggestions = input<BoardSuggestion[]>([]);
  /** Vrai une fois la suggestion revenue, pour distinguer « pas encore lancé » de « aucun résultat ». */
  readonly boardsSuggested = input(false);

  readonly search = output<JobSearchCriteria>();
  readonly smartSearch = output<JobSearchCriteria>();
  readonly matchAll = output<JobSearchCriteria>();
  readonly matchAdvanced = output<JobSearchCriteria>();
  readonly fetchUrl = output<string>();
  /** Demande de suggestion de boards à partir du profil candidat. */
  readonly suggestBoards = output();

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

  /** Chaque suggestion réutilise le même flux que le collage d'URL : son boardUrl est déjà vérifié. */
  onFetchBoard(board: BoardSuggestion): void {
    this.fetchUrl.emit(board.boardUrl);
  }
}
