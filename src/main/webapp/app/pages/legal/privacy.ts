import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslateDirective } from 'app/shared/language';

import { SeoService } from '../shared/seo.service';

@Component({
  selector: 'jhi-legal-privacy',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './privacy.html',
  styleUrl: '../pages.scss',
  imports: [RouterLink, TranslateDirective],
})
export default class LegalPrivacy {
  constructor() {
    inject(SeoService).update({
      title: 'Politique de confidentialité | Job Search AI',
      description:
        'Politique de confidentialité de Job Search AI : données collectées, finalités, durée de conservation et vos droits RGPD.',
      path: '/legal/confidentialite',
    });
  }
}
