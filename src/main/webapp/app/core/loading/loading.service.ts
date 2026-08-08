import { inject, Injectable } from '@angular/core';
import { NgxSpinnerService } from 'ngx-spinner';

/**
 * Tracks the number of in-flight "foreground" HTTP requests and drives the global {@code <ngx-spinner>}.
 * Two safeguards vs a naive show-on-request/hide-on-finalize counter:
 *  - the spinner is only shown after a short delay, so fast requests never flash it;
 *  - it is hidden as soon as the counter reaches zero, and the pending show timer is cancelled,
 *    so the overlay can never get stuck covering the page (which would hide the chat widget).
 */
@Injectable({ providedIn: 'root' })
export class LoadingService {
  private readonly spinner = inject(NgxSpinnerService);

  /** Delay before actually showing, so quick requests don't flash the spinner. */
  private static readonly SHOW_DELAY_MS = 250;

  private activeRequests = 0;
  private showTimer: ReturnType<typeof setTimeout> | null = null;
  private visible = false;

  start(): void {
    this.activeRequests++;
    if (this.activeRequests === 1 && !this.visible && this.showTimer === null) {
      this.showTimer = setTimeout(() => {
        this.showTimer = null;
        if (this.activeRequests > 0) {
          this.visible = true;
          void this.spinner.show();
        }
      }, LoadingService.SHOW_DELAY_MS);
    }
  }

  stop(): void {
    this.activeRequests = Math.max(0, this.activeRequests - 1);
    if (this.activeRequests === 0) {
      if (this.showTimer !== null) {
        clearTimeout(this.showTimer);
        this.showTimer = null;
      }
      if (this.visible) {
        this.visible = false;
        void this.spinner.hide();
      }
    }
  }
}
