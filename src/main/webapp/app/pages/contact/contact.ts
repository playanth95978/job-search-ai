import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { SeoService } from '../shared/seo.service';

@Component({
  selector: 'jhi-contact',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './contact.html',
  styleUrl: '../pages.scss',
  imports: [RouterLink],
})
export default class Contact {
  /** Public support/contact inbox. */
  protected readonly email = 'anthony.attia1@gmail.com';

  constructor() {
    inject(SeoService).update({
      title: 'Contact | Job Search AI',
      description: 'Une question sur Job Search AI ? Contactez notre équipe par e-mail, nous répondons sous 48 heures ouvrées.',
      path: '/contact',
    });
  }
}
