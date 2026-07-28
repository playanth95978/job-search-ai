import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { SeoService } from '../shared/seo.service';

interface FaqItem {
  readonly question: string;
  readonly answer: string;
}

const FAQ_ITEMS: readonly FaqItem[] = [
  {
    question: 'Comment fonctionne le matching entre mon CV et les offres ?',
    answer:
      "Nous analysons le contenu de votre CV et le comparons sémantiquement aux offres : l'IA évalue le sens, pas seulement les mots-clés, pour faire remonter les postes réellement adaptés à votre profil.",
  },
  {
    question: 'Mes données sont-elles en sécurité ?',
    answer:
      'Vos données ne sont jamais revendues. Elles servent uniquement à vous fournir le service (analyse de CV, matching, suivi de candidatures) et vous pouvez demander leur suppression à tout moment.',
  },
  {
    question: 'Le service est-il gratuit ?',
    answer:
      'Vous pouvez importer votre CV et lancer vos premières recherches gratuitement. Des fonctionnalités avancées pourront être proposées ultérieurement.',
  },
  {
    question: 'Quels formats de CV puis-je importer ?',
    answer:
      'Vous pouvez importer un CV au format PDF. Le texte est analysé automatiquement pour en extraire vos compétences et votre expérience.',
  },
  {
    question: "Comment l'assistant m'aide-t-il à préparer un entretien ?",
    answer:
      "L'assistant simule des questions adaptées à l'offre visée et à votre parcours, puis vous donne un retour pour améliorer vos réponses avant le vrai entretien.",
  },
];

@Component({
  selector: 'jhi-faq',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './faq.html',
  styleUrl: '../pages.scss',
  imports: [RouterLink],
})
export default class Faq {
  protected readonly items = FAQ_ITEMS;

  private readonly seo = inject(SeoService);

  constructor() {
    this.seo.update({
      title: 'FAQ | Job Search AI',
      description:
        'Questions fréquentes sur Job Search AI : matching CV/offres, sécurité des données, formats acceptés et préparation aux entretiens.',
      path: '/faq',
    });
    this.injectStructuredData();
  }

  /** Adds FAQPage JSON-LD so Google can surface rich results. */
  private injectStructuredData(): void {
    this.seo.setJsonLd('faq-jsonld', {
      '@context': 'https://schema.org',
      '@type': 'FAQPage',
      mainEntity: this.items.map(item => ({
        '@type': 'Question',
        name: item.question,
        acceptedAnswer: { '@type': 'Answer', text: item.answer },
      })),
    });
  }
}
