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
  /** Absolute URL or root-relative path of the social preview image. Defaults to the site image. */
  readonly image?: string;
  /** Open Graph object type. Defaults to {@code website}. */
  readonly type?: 'website' | 'article';
  /** Optional comma-separated keywords. */
  readonly keywords?: string;
}

/**
 * Centralises per-page SEO metadata (title, description, canonical, Open Graph, Twitter Card)
 * and structured data (JSON-LD).
 *
 * Note: this app is a client-rendered SPA. Googlebot renders the JS, so these tags are picked
 * up on indexing. For the strongest results, pair this with the backend sitemap
 * ({@code /sitemap.xml}) and robots.txt already shipped with the app.
 */
@Injectable({ providedIn: 'root' })
export class SeoService {
  /** Public origin used to build canonical / og / twitter URLs. */
  static readonly ORIGIN = 'https://aicarreerfinder.com';
  /** Brand name used for og:site_name and title composition. */
  private static readonly SITE_NAME = 'Job Search AI';
  /** Default social preview image (must be a same-origin, publicly reachable asset). */
  private static readonly DEFAULT_IMAGE = '/content/images/og-default.png';
  /** Open Graph locale. */
  private static readonly LOCALE = 'fr_FR';

  private readonly meta = inject(Meta);
  private readonly title = inject(Title);
  private readonly document = inject(DOCUMENT);

  update({ title, description, path, image, type = 'website', keywords }: SeoData): void {
    const url = this.absoluteUrl(path);
    const imageUrl = this.absoluteUrl(image ?? SeoService.DEFAULT_IMAGE);

    this.title.setTitle(title);
    this.meta.updateTag({ name: 'description', content: description });
    if (keywords) {
      this.meta.updateTag({ name: 'keywords', content: keywords });
    }

    // Open Graph
    this.meta.updateTag({ property: 'og:type', content: type });
    this.meta.updateTag({ property: 'og:site_name', content: SeoService.SITE_NAME });
    this.meta.updateTag({ property: 'og:locale', content: SeoService.LOCALE });
    this.meta.updateTag({ property: 'og:title', content: title });
    this.meta.updateTag({ property: 'og:description', content: description });
    this.meta.updateTag({ property: 'og:url', content: url });
    this.meta.updateTag({ property: 'og:image', content: imageUrl });

    // Twitter Card
    this.meta.updateTag({ name: 'twitter:card', content: 'summary_large_image' });
    this.meta.updateTag({ name: 'twitter:title', content: title });
    this.meta.updateTag({ name: 'twitter:description', content: description });
    this.meta.updateTag({ name: 'twitter:image', content: imageUrl });

    this.setCanonical(url);
  }

  /** Injects (or replaces) a JSON-LD structured-data block identified by {@code id}. */
  setJsonLd(id: string, schema: Record<string, unknown>): void {
    this.removeJsonLd(id);
    const script = this.document.createElement('script');
    script.id = id;
    script.type = 'application/ld+json';
    script.text = JSON.stringify(schema);
    this.document.head.appendChild(script);
  }

  /** Removes a previously injected JSON-LD block. */
  removeJsonLd(id: string): void {
    this.document.getElementById(id)?.remove();
  }

  /** Turns a root-relative path or an absolute URL into an absolute canonical URL. */
  private absoluteUrl(pathOrUrl: string): string {
    if (/^https?:\/\//.test(pathOrUrl)) {
      return pathOrUrl;
    }
    return `${SeoService.ORIGIN}${pathOrUrl.startsWith('/') ? pathOrUrl : `/${pathOrUrl}`}`;
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
