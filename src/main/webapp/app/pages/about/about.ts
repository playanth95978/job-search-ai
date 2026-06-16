import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { SeoService } from '../shared/seo.service';

@Component({
  selector: 'jhi-about',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './about.html',
  styleUrl: '../pages.scss',
  imports: [RouterLink],
})
export default class About {
  constructor() {
    inject(SeoService).update({
      title: 'À propos | Job Search AI',
      description:
        "Job Search AI accélère votre recherche d'emploi grâce à l'intelligence artificielle : analyse de CV, matching d'offres et préparation aux entretiens.",
      path: '/a-propos',
    });
  }
}
