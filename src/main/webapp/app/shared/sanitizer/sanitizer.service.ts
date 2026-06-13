// src/app/services/sanitizer.service.ts
import { inject, Injectable } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import dompurify from 'dompurify';

@Injectable({
  providedIn: 'root',
})
export class SanitizerService {
  private sanitizer: DomSanitizer = inject(DomSanitizer);

  sanitizeHtml(html: string): SafeHtml {
    const cleanHtml = dompurify.sanitize(html, {
      ALLOWED_TAGS: [
        // Structure
        'div',
        'span',
        'p',
        'br',
        'hr',

        // Texte
        'b',
        'i',
        'u',
        'strong',
        'em',
        'mark',
        'small',

        // Listes
        'ul',
        'ol',
        'li',

        // Titres
        'h1',
        'h2',
        'h3',
        'h4',
        'h5',
        'h6',

        // Liens
        'a',

        // Code (CRUCIAL)
        'pre',
        'code',

        // Table
        'table',
        'thead',
        'tbody',
        'tr',
        'th',
        'td',

        // Images (optionnel)
        'img',

        // Boutons (pour ton copy)
        'button',

        // Mermaid / SVG
        'svg',
        'path',
        'g',
        'circle',
        'rect',
        'line',
        'polygon',
        'text',
      ],

      ALLOWED_ATTR: [
        // Global
        'class',
        'id',
        'style',

        // Liens
        'href',
        'target',
        'rel',

        // Images
        'src',
        'alt',

        // Code highlight
        'data-language',

        // Mermaid / SVG
        'viewBox',
        'fill',
        'stroke',
        'd',
        'x',
        'y',
        'cx',
        'cy',
        'r',
        'width',
        'height',

        // Custom (si tu utilises)
        'data-*',
      ],

      FORBID_ATTR: ['onerror', 'onclick', 'onload', 'onmouseover'],

      ALLOW_DATA_ATTR: true,
    });
    return this.sanitizer.bypassSecurityTrustHtml(cleanHtml);
  }
}
