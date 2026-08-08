import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { LowerCasePipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';

import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateModule } from '@ngx-translate/core';

import { AccountService } from 'app/core/auth/account.service';
import { SeoService } from 'app/pages/shared/seo.service';
import { TranslateDirective } from 'app/shared/language';
import { RevealOnScrollDirective } from 'app/shared/reveal/reveal-on-scroll.directive';
import { applicationStatusColor, applicationStatusIcon, ApplicationStats, JobOffer } from 'app/job-copilot/shared/job-copilot.model';
import { JobCopilotService } from 'app/job-copilot/shared/job-copilot.service';
import { JOB_COPILOT_NAV } from 'app/job-copilot/shared/job-copilot-nav';
import { HomeBanniere } from './home-banniere/home-banniere';
import { HomeLanding } from './home-landing/home-landing';

@Component({
  selector: 'jhi-home',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './home.html',
  styleUrl: './home.scss',
  imports: [
    TranslateDirective,
    TranslateModule,
    RouterLink,
    FaIconComponent,
    LowerCasePipe,
    RevealOnScrollDirective,
    HomeBanniere,
    HomeLanding,
  ],
})
export default class Home {
  public readonly account = inject(AccountService).account;

  protected readonly quickAccessItems = JOB_COPILOT_NAV;
  protected readonly statsEntries = computed(() => Object.entries(this.stats()).filter(([, value]) => value > 0));

  private readonly router = inject(Router);
  private readonly jobCopilotService = inject(JobCopilotService);
  private readonly seo = inject(SeoService);

  private readonly stats = signal<ApplicationStats>({});

  /** Public count of AI-indexed offers, shown as a corpus-size stat in the banner. */
  protected readonly indexedJobCount = signal<number | null>(null);

  /** Live board opportunities matched to the user's profile (logged-in home feed). */
  protected readonly liveOpportunities = signal<JobOffer[]>([]);
  protected readonly liveLoading = signal(false);
  protected readonly liveLoaded = signal(false);

  constructor() {
    this.seo.update({
      title: 'Job Search AI — Recherche d’emploi et copilote de carrière IA',
      description:
        "Importez votre CV, recevez des offres d'emploi pertinentes grâce à l'IA, générez des lettres de motivation sur-mesure et préparez vos entretiens.",
      path: '/',
      keywords: "recherche d'emploi, offres d'emploi, IA, matching CV, lettre de motivation, préparation entretien, copilote carrière",
    });
    this.injectStructuredData();

    // Public stat: works for anonymous visitors too, so it fills the landing hero.
    this.jobCopilotService.getIndexedJobCount().subscribe({
      next: ({ count }) => this.indexedJobCount.set(count),
      error: () => this.indexedJobCount.set(null),
    });

    if (this.account() !== null) {
      this.jobCopilotService.getApplicationStats().subscribe({
        next: stats => this.stats.set(stats),
        error: () => this.stats.set({}),
      });
      this.loadLiveOpportunities();
    }
  }

  /** Loads the live opportunity feed asynchronously so it never blocks the initial home render. */
  loadLiveOpportunities(refresh = false): void {
    if (this.liveLoading()) return;
    this.liveLoading.set(true);
    this.jobCopilotService.getLiveOpportunities(refresh).subscribe({
      next: offers => {
        this.liveOpportunities.set(offers);
        this.liveLoading.set(false);
        this.liveLoaded.set(true);
      },
      error: () => {
        this.liveOpportunities.set([]);
        this.liveLoading.set(false);
        this.liveLoaded.set(true);
      },
    });
  }

  login(): void {
    this.router.navigate(['/login']);
  }

  statusIcon(status: string): string {
    return applicationStatusIcon(status);
  }

  statusColor(status: string): string {
    return applicationStatusColor(status);
  }

  /** Organization + WebSite structured data so Google can build a Knowledge Panel / brand entity. */
  private injectStructuredData(): void {
    const origin = SeoService.ORIGIN;
    this.seo.setJsonLd('home-jsonld', {
      '@context': 'https://schema.org',
      '@graph': [
        {
          '@type': 'Organization',
          '@id': `${origin}/#organization`,
          name: 'Job Search AI',
          url: origin,
          logo: `${origin}/content/images/og-default.png`,
          description: "Plateforme de recherche d'emploi et copilote de carrière propulsés par l'intelligence artificielle.",
        },
        {
          '@type': 'WebSite',
          '@id': `${origin}/#website`,
          name: 'Job Search AI',
          url: origin,
          inLanguage: 'fr-FR',
          publisher: { '@id': `${origin}/#organization` },
        },
      ],
    });
  }
}
