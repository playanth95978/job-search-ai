import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslateDirective } from 'app/shared/language';

import { SeoService } from '../shared/seo.service';

@Component({
  selector: 'jhi-legal-terms',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './terms.html',
  styleUrl: '../pages.scss',
  imports: [RouterLink, TranslateDirective],
})
export default class LegalTerms {
  constructor() {
    inject(SeoService).update({
      title: "Conditions d'utilisation | Job Search AI",
      description: "Conditions générales d'utilisation du service Job Search AI : accès, compte, obligations et responsabilités.",
      path: '/legal/conditions-utilisation',
    });
  }
}
