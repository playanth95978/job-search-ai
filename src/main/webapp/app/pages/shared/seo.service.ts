import { DOCUMENT } from '@angular/common';
import { inject, Injectable } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';

export interface SeoData {
  /** Used for the document title and og:title. */
  readonly title: string;
  /** ~150 chars meta description for search snippets. */
  readonly description: string;
  /** Absolute path of the page, e.g. {@code /a-propos}. */
  readonly path: string;
}

/**
 * Centralises per-page SEO metadata (title, description, canonical, Open Graph).
 *
 * Note: this app is a client-rendered SPA. For reliable indexing of these public
 * pages, pair this with prerendering/SSR — these tags only help once the JS has run.
 */
@Injectable({ providedIn: 'root' })
export class SeoService {
  /** Public origin used to build canonical/og URLs. Adjust to the production domain. */
  private static readonly ORIGIN = 'https://www.job-search-ai.com';

  private readonly meta = inject(Meta);
  private readonly title = inject(Title);
  private readonly document = inject(DOCUMENT);

  update({ title, description, path }: SeoData): void {
    const url = `${SeoService.ORIGIN}${path}`;

    this.title.setTitle(title);
    this.meta.updateTag({ name: 'description', content: description });
    this.meta.updateTag({ property: 'og:type', content: 'website' });
    this.meta.updateTag({ property: 'og:title', content: title });
    this.meta.updateTag({ property: 'og:description', content: description });
    this.meta.updateTag({ property: 'og:url', content: url });
    this.setCanonical(url);
  }

  private setCanonical(url: string): void {
    let link = this.document.querySelector<HTMLLinkElement>('link[rel="canonical"]');
    if (!link) {
      link = this.document.createElement('link');
      link.setAttribute('rel', 'canonical');
      this.document.head.appendChild(link);
    }
    link.setAttribute('href', url);
  }
}
