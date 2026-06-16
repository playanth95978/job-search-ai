import { Routes } from '@angular/router';

/**
 * Public, statically-rendered content pages linked from the footer.
 * Paths are French/SEO-friendly slugs.
 */
const routes: Routes = [
  {
    path: 'a-propos',
    loadComponent: () => import('./about/about'),
    title: 'pages.about.title',
  },
  {
    path: 'contact',
    loadComponent: () => import('./contact/contact'),
    title: 'pages.contact.title',
  },
  {
    path: 'faq',
    loadComponent: () => import('./faq/faq'),
    title: 'pages.faq.title',
  },
  {
    path: 'legal/conditions-utilisation',
    loadComponent: () => import('./legal/terms'),
    title: 'pages.terms.title',
  },
  {
    path: 'legal/confidentialite',
    loadComponent: () => import('./legal/privacy'),
    title: 'pages.privacy.title',
  },
  {
    path: 'legal/cookies',
    loadComponent: () => import('./legal/cookies'),
    title: 'pages.cookies.title',
  },
  {
    path: 'legal/mentions-legales',
    loadComponent: () => import('./legal/notice'),
    title: 'pages.notice.title',
  },
];

export default routes;
