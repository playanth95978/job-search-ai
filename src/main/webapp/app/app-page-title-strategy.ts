import { Injectable, inject } from '@angular/core';
import { Meta } from '@angular/platform-browser';
import { RouterStateSnapshot, TitleStrategy } from '@angular/router';

import { TranslateService } from '@ngx-translate/core';

/**
 * Public paths that should be crawled and indexed by search engines.
 * Everything else (dashboards, account, admin, chat, onboarding, login…) is
 * behind auth or of no SEO value and is marked {@code noindex}.
 */
const INDEXABLE_EXACT_PATHS = new Set(['/', '/a-propos', '/contact', '/faq', '/search']);
const INDEXABLE_PREFIXES = ['/legal/'];

@Injectable()
export class AppPageTitleStrategy extends TitleStrategy {
  private readonly translateService = inject(TranslateService);
  private readonly meta = inject(Meta);

  override updateTitle(routerState: RouterStateSnapshot): void {
    let pageTitle = this.buildTitle(routerState);
    pageTitle ??= 'global.title';
    this.translateService.get(pageTitle).subscribe(title => {
      document.title = title;
    });

    this.updateRobots(routerState.url);
  }

  /** Keeps public pages indexable and hides the private/app area from search engines. */
  private updateRobots(url: string): void {
    const path = url.split(/[?#]/)[0];
    const indexable = INDEXABLE_EXACT_PATHS.has(path) || INDEXABLE_PREFIXES.some(prefix => path.startsWith(prefix));
    this.meta.updateTag({ name: 'robots', content: indexable ? 'index, follow' : 'noindex, nofollow' });
  }
}
