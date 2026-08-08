import { HttpContextToken, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { finalize } from 'rxjs/operators';

import { LoadingService } from 'app/core/loading/loading.service';

/**
 * Opt a single request out of the global loading spinner:
 * {@code http.get(url, { context: new HttpContext().set(SKIP_LOADING, true) })}.
 */
export const SKIP_LOADING = new HttpContextToken<boolean>(() => false);

/**
 * URL fragments that must never trigger the global spinner: long-lived streams and background
 * feeds. Showing a full-screen overlay for these would either never dismiss (open SSE streams)
 * or block the whole page while a background feed loads (the home has its own inline loader).
 */
const SILENT_URL_FRAGMENTS = ['/assistant/', '/stream', '/opportunities/live', '/interview/', '/panel/', '/mistral-ocr/', '/management/'];

/**
 * Global loading interceptor. Shows the shared {@code <ngx-spinner>} while foreground requests are
 * in flight, but skips streaming/progress requests (SSE) and background feeds so the overlay can't
 * get stuck on top of the UI (which previously hid the floating chat widget).
 */
export const loadingInterceptor: HttpInterceptorFn = (req, next) => {
  const silent =
    req.context.get(SKIP_LOADING) ||
    req.reportProgress || // streaming / progress-reported requests (SSE token streams)
    SILENT_URL_FRAGMENTS.some(fragment => req.url.includes(fragment));

  if (silent) {
    return next(req);
  }

  const loading = inject(LoadingService);
  loading.start();
  return next(req).pipe(finalize(() => loading.stop()));
};
