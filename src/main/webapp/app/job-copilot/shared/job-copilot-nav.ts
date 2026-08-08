/**
 * Single source of truth for the Job Copilot quick-access links, shared by the home page
 * quick-access grid and the left navigation rail so both stay in sync.
 */
export interface JobCopilotNavItem {
  routerLink: string;
  icon: string;
  titleKey: string;
  subtitleKey: string;
  /** Cosmetic class used by the home quick-access cards (ignored by the rail). */
  cssClass: string;
}

export const JOB_COPILOT_NAV: readonly JobCopilotNavItem[] = [
  {
    routerLink: '/job-copilot/profile',
    icon: 'user-circle',
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
    routerLink: '/job-copilot/matches',
    icon: 'bullseye',
    cssClass: 'jc-action-search',
    titleKey: 'home.quickAccess.allMatches.title',
    subtitleKey: 'home.quickAccess.allMatches.subtitle',
  },
  {
    routerLink: '/job-copilot/auto-apply',
    icon: 'robot',
    cssClass: 'jc-action-apps',
    titleKey: 'home.quickAccess.autoApply.title',
    subtitleKey: 'home.quickAccess.autoApply.subtitle',
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
