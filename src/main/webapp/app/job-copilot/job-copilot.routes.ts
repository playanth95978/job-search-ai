import { Routes } from '@angular/router';

const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./dashboard/dashboard.component').then(m => m.DashboardComponent),
    title: 'Job Copilot',
  },
  {
    path: 'onboarding',
    loadComponent: () => import('./onboarding/onboarding.component').then(m => m.OnboardingComponent),
    title: 'Upload CV',
  },
  {
    path: 'profile',
    loadComponent: () => import('./profile/profile.component').then(m => m.ProfileComponent),
    title: 'jobCopilot.profile.title',
  },
  {
    path: 'cv-builder',
    loadComponent: () => import('./cv-builder/cv-builder.component').then(m => m.CvBuilderComponent),
    title: 'cvBuilder.title',
  },
  {
    path: 'matches',
    loadComponent: () => import('./matches/matches.component').then(m => m.MatchesComponent),
    title: 'jobCopilot.matches.title',
  },
  {
    path: 'auto-apply',
    loadComponent: () => import('./auto-apply/auto-apply.component').then(m => m.AutoApplyComponent),
    title: 'jobCopilot.autoApply.title',
  },
  {
    path: 'search',
    loadComponent: () => import('./job-search/job-search.component').then(m => m.JobSearchComponent),
    title: 'Job Search',
  },
  {
    path: 'applications',
    loadComponent: () => import('./applications/applications.component').then(m => m.ApplicationsComponent),
    title: 'Applications',
  },
  {
    path: 'insights',
    loadComponent: () => import('./insights/insights.component').then(m => m.InsightsComponent),
    title: 'Insights',
  },
  {
    path: 'radar',
    loadComponent: () => import('./radar/radar.component').then(m => m.RadarComponent),
    title: 'Opportunity Radar',
  },
  {
    path: 'settings',
    loadComponent: () => import('./settings/settings.component').then(m => m.SettingsComponent),
    title: 'Preferences',
  },
  {
    path: 'career',
    loadComponent: () => import('./career/career-path.component').then(m => m.CareerPathComponent),
    title: 'Career Simulator',
  },
  {
    path: 'interview',
    loadComponent: () => import('./interview/interview.component').then(m => m.InterviewComponent),
    title: 'Interview Roleplay',
  },
  {
    path: 'interview-panel',
    loadComponent: () => import('./interview-panel/interview-panel.component').then(m => m.InterviewPanelComponent),
    title: 'Interview Panel',
  },
];

export default routes;
