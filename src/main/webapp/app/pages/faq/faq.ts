import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslateDirective } from 'app/shared/language';

import { SeoService } from '../shared/seo.service';
import { TranslateService } from '@ngx-translate/core';

interface FaqItem {
  readonly questionKey: string;
  readonly answerKey: string;
}

const FAQ_ITEMS: readonly FaqItem[] = [
  {
    questionKey: 'pages.faq.items.matching.question',
    answerKey: 'pages.faq.items.matching.answer',
  },
  {
    questionKey: 'pages.faq.items.security.question',
    answerKey: 'pages.faq.items.security.answer',
  },
  {
    questionKey: 'pages.faq.items.price.question',
    answerKey: 'pages.faq.items.price.answer',
  },
  {
    questionKey: 'pages.faq.items.formats.question',
    answerKey: 'pages.faq.items.formats.answer',
  },
  {
    questionKey: 'pages.faq.items.interview.question',
    answerKey: 'pages.faq.items.interview.answer',
  },
];

@Component({
  selector: 'jhi-faq',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './faq.html',
  styleUrl: '../pages.scss',
  imports: [RouterLink, TranslateDirective],
})
export default class Faq {
  protected readonly items = FAQ_ITEMS;

  private readonly seo = inject(SeoService);
  private readonly translateService = inject(TranslateService);

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
    const translatedItems = this.items.map(item => ({
      question: this.translateService.instant(item.questionKey),
      answer: this.translateService.instant(item.answerKey),
    }));

    this.seo.setJsonLd('faq-jsonld', {
      '@context': 'https://schema.org',
      '@type': 'FAQPage',
      mainEntity: translatedItems.map(item => ({
        '@type': 'Question',
        name: item.question,
        acceptedAnswer: { '@type': 'Answer', text: item.answer },
      })),
    });
  }
}
