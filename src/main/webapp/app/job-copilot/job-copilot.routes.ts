import { Routes } from '@angular/router';

const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./dashboard/dashboard.component').then(m => m.DashboardComponent),
    title: 'jobCopilot.dashboard.title',
  },
  {
    path: 'onboarding',
    redirectTo: '',
    pathMatch: 'full',
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
    path: 'search',
    loadComponent: () => import('./job-search/job-search.component').then(m => m.JobSearchComponent),
    title: 'jobCopilot.search.title',
  },
  {
    path: 'applications',
    loadComponent: () => import('./applications/applications.component').then(m => m.ApplicationsComponent),
    title: 'jobCopilot.applications.title',
  },
  {
    path: 'insights',
    loadComponent: () => import('./insights/insights.component').then(m => m.InsightsComponent),
    title: 'jobCopilot.insights.title',
  },
  {
    path: 'radar',
    loadComponent: () => import('./radar/radar.component').then(m => m.RadarComponent),
    title: 'jobCopilot.radar.title',
  },
  {
    path: 'settings',
    loadComponent: () => import('./settings/settings.component').then(m => m.SettingsComponent),
    title: 'jobCopilot.settings.title',
  },
  {
    path: 'career',
    loadComponent: () => import('./career/career-path.component').then(m => m.CareerPathComponent),
    title: 'jobCopilot.career.title',
  },
  {
    path: 'interview',
    loadComponent: () => import('./interview/interview.component').then(m => m.InterviewComponent),
    title: 'jobCopilot.interview.title',
  },
  {
    path: 'interview-panel',
    loadComponent: () => import('./interview-panel/interview-panel.component').then(m => m.InterviewPanelComponent),
    title: 'jobCopilot.panel.title',
  },
];

export default routes;
