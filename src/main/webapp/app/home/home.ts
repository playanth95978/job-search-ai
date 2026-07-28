import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { LowerCasePipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';

import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateModule } from '@ngx-translate/core';

import { AccountService } from 'app/core/auth/account.service';
import { SeoService } from 'app/pages/shared/seo.service';
import { TranslateDirective } from 'app/shared/language';
import { RevealOnScrollDirective } from 'app/shared/reveal/reveal-on-scroll.directive';
import { applicationStatusColor, applicationStatusIcon, ApplicationStats } from 'app/job-copilot/shared/job-copilot.model';
import { JobCopilotService } from 'app/job-copilot/shared/job-copilot.service';
import { HomeBanniere } from './home-banniere/home-banniere';
import { HomeLanding } from './home-landing/home-landing';

interface QuickAccessItem {
  routerLink: string;
  icon: string;
  cssClass: string;
  titleKey: string;
  subtitleKey: string;
}

const QUICK_ACCESS_ITEMS: QuickAccessItem[] = [
  {
    routerLink: '/job-copilot/onboarding',
    icon: 'file-arrow-up',
    cssClass: 'jc-action-onboarding',
    titleKey: 'home.quickAccess.onboarding.title',
    subtitleKey: 'home.quickAccess.onboarding.subtitle',
  },
  {
    routerLink: '/job-copilot/cv-builder',
    icon: 'file-alt',
    cssClass: 'jc-action-onboarding',
    titleKey: 'home.quickAccess.cvBuilder.title',
    subtitleKey: 'home.quickAccess.cvBuilder.subtitle',
  },
  {
    routerLink: '/job-copilot/search',
    icon: 'search',
    cssClass: 'jc-action-search',
    titleKey: 'home.quickAccess.search.title',
    subtitleKey: 'home.quickAccess.search.subtitle',
  },
  {
    routerLink: '/job-copilot/applications',
    icon: 'clipboard-list',
    cssClass: 'jc-action-apps',
    titleKey: 'home.quickAccess.applications.title',
    subtitleKey: 'home.quickAccess.applications.subtitle',
  },
  {
    routerLink: '/job-copilot/interview',
    icon: 'user-tie',
    cssClass: 'jc-action-interview',
    titleKey: 'home.quickAccess.interview.title',
    subtitleKey: 'home.quickAccess.interview.subtitle',
  },
  {
    routerLink: '/job-copilot/settings',
    icon: 'cogs',
    cssClass: 'jc-action-settings',
    titleKey: 'home.quickAccess.settings.title',
    subtitleKey: 'home.quickAccess.settings.subtitle',
  },
  {
    routerLink: '/chat',
    icon: 'comments',
    cssClass: 'jc-action-chat',
    titleKey: 'home.quickAccess.chat.title',
    subtitleKey: 'home.quickAccess.chat.subtitle',
  },
];

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

  protected readonly quickAccessItems = QUICK_ACCESS_ITEMS;
  protected readonly statsEntries = computed(() => Object.entries(this.stats()).filter(([, value]) => value > 0));

  private readonly router = inject(Router);
  private readonly jobCopilotService = inject(JobCopilotService);
  private readonly seo = inject(SeoService);

  private readonly stats = signal<ApplicationStats>({});

  constructor() {
    this.seo.update({
      title: 'Job Search AI — Recherche d’emploi et copilote de carrière IA',
      description:
        "Importez votre CV, recevez des offres d'emploi pertinentes grâce à l'IA, générez des lettres de motivation sur-mesure et préparez vos entretiens.",
      path: '/',
      keywords: "recherche d'emploi, offres d'emploi, IA, matching CV, lettre de motivation, préparation entretien, copilote carrière",
    });
    this.injectStructuredData();

    if (this.account() !== null) {
      this.jobCopilotService.getApplicationStats().subscribe({
        next: stats => this.stats.set(stats),
        error: () => this.stats.set({}),
      });
    }
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
