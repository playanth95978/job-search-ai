import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslateDirective } from 'app/shared/language';

import { SeoService } from '../shared/seo.service';

@Component({
  selector: 'jhi-legal-notice',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './notice.html',
  styleUrl: '../pages.scss',
  imports: [RouterLink, TranslateDirective],
})
export default class LegalNotice {
  constructor() {
    inject(SeoService).update({
      title: 'Mentions légales | Job Search AI',
      description: 'Mentions légales de Job Search AI : éditeur du site, directeur de la publication et hébergeur.',
      path: '/legal/mentions-legales',
    });
  }
}
