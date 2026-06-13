import { Component } from '@angular/core';
import { RouterModule } from '@angular/router';

import { TranslateDirective } from 'app/shared/language';

@Component({
  selector: 'jhi-footer',
  templateUrl: './footer.html',
  styleUrl: './footer.scss',
  imports: [TranslateDirective, RouterModule],
})
export default class Footer {}
