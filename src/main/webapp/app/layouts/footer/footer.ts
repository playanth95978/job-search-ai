import { Component } from '@angular/core';
import { RouterModule } from '@angular/router';

import { TranslateDirective } from 'app/shared/language';

interface FooterLink {
  readonly labelKey: string;
  readonly label: string;
  readonly routerLink?: string;
  readonly href?: string;
}

interface FooterSection {
  readonly titleKey: string;
  readonly title: string;
  readonly links: readonly FooterLink[];
}

const FOOTER_SECTIONS: readonly FooterSection[] = [
  {
    titleKey: 'footer.navigation.title',
    title: 'Navigation',
    links: [
      { labelKey: 'footer.navigation.home', label: 'Accueil', routerLink: '/' },
      { labelKey: 'footer.navigation.jobs', label: "Offres d'emploi", routerLink: '/job-copilot/search' },
      { labelKey: 'footer.navigation.companies', label: 'Entreprises', href: '/entreprises' },
      { labelKey: 'footer.navigation.aiAssistant', label: 'Assistant IA', routerLink: '/chat' },
      { labelKey: 'footer.navigation.uploadCv', label: 'Importer mon CV', routerLink: '/job-copilot/onboarding' },
      { labelKey: 'footer.navigation.dashboard', label: 'Tableau de bord', routerLink: '/job-copilot' },
    ],
  },
  {
    titleKey: 'footer.resources.title',
    title: 'Ressources',
    links: [
      { labelKey: 'footer.resources.careerAdvice', label: 'Conseils carrière', href: '/conseils-carriere' },
      { labelKey: 'footer.resources.cvGuide', label: 'Guide CV', routerLink: '/job-copilot/cv-builder' },
      { labelKey: 'footer.resources.interviewPrep', label: 'Préparation aux entretiens', routerLink: '/job-copilot/interview' },
      { labelKey: 'footer.resources.faq', label: 'FAQ', href: '/faq' },
      { labelKey: 'footer.resources.blog', label: 'Blog', href: '/blog' },
    ],
  },
  {
    titleKey: 'footer.legal.title',
    title: 'Légal',
    links: [
      { labelKey: 'footer.legal.terms', label: "Conditions d'utilisation", href: '/legal/conditions-utilisation' },
      { labelKey: 'footer.legal.privacy', label: 'Politique de confidentialité', href: '/legal/confidentialite' },
      { labelKey: 'footer.legal.cookies', label: 'Cookies', href: '/legal/cookies' },
      { labelKey: 'footer.legal.notice', label: 'Mentions légales', href: '/legal/mentions-legales' },
    ],
  },
  {
    titleKey: 'footer.company.title',
    title: 'Entreprise',
    links: [
      { labelKey: 'footer.company.about', label: 'À propos', href: '/a-propos' },
      { labelKey: 'footer.company.contact', label: 'Contact', href: '/contact' },
      { labelKey: 'footer.company.partners', label: 'Partenaires', href: '/partenaires' },
      { labelKey: 'footer.company.press', label: 'Presse', href: '/presse' },
    ],
  },
];

const AI_SEARCH_LINKS: readonly FooterLink[] = [
  { labelKey: 'footer.aiSearch.cvAnalysis', label: 'Analyse de CV', routerLink: '/job-copilot/onboarding' },
  { labelKey: 'footer.aiSearch.matching', label: 'Matching CV ↔ Offres', routerLink: '/job-copilot/search' },
  { labelKey: 'footer.aiSearch.coverLetter', label: 'Générateur de lettres de motivation', routerLink: '/job-copilot/cv-builder' },
  { labelKey: 'footer.aiSearch.interviewSimulator', label: "Simulateur d'entretien", routerLink: '/job-copilot/interview' },
];

@Component({
  selector: 'jhi-footer',
  templateUrl: './footer.html',
  styleUrl: './footer.scss',
  imports: [TranslateDirective, RouterModule],
})
export default class Footer {
  protected readonly currentYear = new Date().getFullYear();
  protected readonly sections = FOOTER_SECTIONS;
  protected readonly aiSearchLinks = AI_SEARCH_LINKS;
}
