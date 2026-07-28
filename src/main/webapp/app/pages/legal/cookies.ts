import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslateDirective } from 'app/shared/language';

import { SeoService } from '../shared/seo.service';

@Component({
  selector: 'jhi-legal-cookies',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './cookies.html',
  styleUrl: '../pages.scss',
  imports: [RouterLink, TranslateDirective],
})
export default class LegalCookies {
  constructor() {
    inject(SeoService).update({
      title: 'Politique de cookies | Job Search AI',
      description: 'Politique de cookies de Job Search AI : types de cookies utilisés, finalités et gestion de vos préférences.',
      path: '/legal/cookies',
    });
  }
}
