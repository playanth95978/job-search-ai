import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { LowerCasePipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';

import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateModule } from '@ngx-translate/core';

import { AccountService } from 'app/core/auth/account.service';
import { TranslateDirective } from 'app/shared/language';
import { applicationStatusColor, applicationStatusIcon, ApplicationStats } from 'app/job-copilot/shared/job-copilot.model';
import { JobCopilotService } from 'app/job-copilot/shared/job-copilot.service';

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
  imports: [TranslateDirective, TranslateModule, RouterLink, FaIconComponent, LowerCasePipe],
})
export default class Home {
  public readonly account = inject(AccountService).account;

  protected readonly quickAccessItems = QUICK_ACCESS_ITEMS;

  private readonly router = inject(Router);
  private readonly jobCopilotService = inject(JobCopilotService);

  private readonly stats = signal<ApplicationStats>({});
  protected readonly statsEntries = computed(() => Object.entries(this.stats()).filter(([, value]) => value > 0));

  constructor() {
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
}
