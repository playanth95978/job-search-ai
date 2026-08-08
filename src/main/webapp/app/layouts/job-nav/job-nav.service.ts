import { Injectable, signal } from '@angular/core';

/**
 * UI state of the left navigation rail, shared between the rail component and the main layout
 * (which shifts its content according to the collapsed width). Signal-based; the collapsed
 * preference is persisted to localStorage so it survives reloads.
 */
@Injectable({ providedIn: 'root' })
export class JobNavService {
  private static readonly STORAGE_KEY = 'jobNavCollapsed';

  /** Desktop: rail collapsed to icons only. */
  readonly collapsed = signal<boolean>(this.readCollapsed());
  /** Mobile: off-canvas drawer open. */
  readonly mobileOpen = signal(false);

  toggleCollapsed(): void {
    this.collapsed.update(value => !value);
    this.persist();
  }

  openMobile(): void {
    this.mobileOpen.set(true);
  }

  closeMobile(): void {
    this.mobileOpen.set(false);
  }

  toggleMobile(): void {
    this.mobileOpen.update(value => !value);
  }

  private readCollapsed(): boolean {
    try {
      return localStorage.getItem(JobNavService.STORAGE_KEY) === 'true';
    } catch {
      return false;
    }
  }

  private persist(): void {
    try {
      localStorage.setItem(JobNavService.STORAGE_KEY, String(this.collapsed()));
    } catch {
      // localStorage unavailable (private mode…) — collapsing simply won't persist.
    }
  }
}
