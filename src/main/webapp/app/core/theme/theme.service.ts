import { Injectable, signal } from '@angular/core';

export type Theme = 'light' | 'dark';

const STORAGE_KEY = 'jsa.theme';

/**
 * Global light/dark theme, applied via Bootstrap 5.3's {@code data-bs-theme} attribute on
 * {@code <html>} (so every {@code var(--bs-*)} surface flips automatically). The chosen theme is
 * persisted; with no stored choice we follow the OS preference and keep tracking it until the user
 * picks one explicitly.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly theme = signal<Theme>('light');

  private readonly media = typeof window !== 'undefined' && window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)') : null;

  /** Called once at startup (APP_INITIALIZER) to resolve and apply the initial theme. */
  init(): void {
    const stored = this.read();
    if (stored) {
      this.apply(stored);
    } else {
      this.apply(this.media?.matches ? 'dark' : 'light');
      // Keep following the OS until the user makes an explicit choice.
      this.media?.addEventListener('change', e => {
        if (!this.read()) {
          this.apply(e.matches ? 'dark' : 'light');
        }
      });
    }
  }

  toggle(): void {
    this.set(this.theme() === 'dark' ? 'light' : 'dark');
  }

  /** Sets an explicit, persisted theme choice. */
  set(theme: Theme): void {
    try {
      localStorage.setItem(STORAGE_KEY, theme);
    } catch {
      // localStorage unavailable — keep in-memory only.
    }
    this.apply(theme);
  }

  private apply(theme: Theme): void {
    this.theme.set(theme);
    document.documentElement.setAttribute('data-bs-theme', theme);
  }

  private read(): Theme | null {
    try {
      const v = localStorage.getItem(STORAGE_KEY);
      return v === 'light' || v === 'dark' ? v : null;
    } catch {
      return null;
    }
  }
}
