import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, LOCALE_ID, importProvidersFrom, inject, provideAppInitializer } from '@angular/core';
import { Title } from '@angular/platform-browser';
import {
  NavigationError,
  Router,
  RouterFeatures,
  TitleStrategy,
  provideRouter,
  withComponentInputBinding,
  withDebugTracing,
  withNavigationErrorHandler,
} from '@angular/router';
import { provideServiceWorker } from '@angular/service-worker';

import { NgbDateAdapter } from '@ng-bootstrap/ng-bootstrap/datepicker';
import { environment } from 'environments/environment';

import { authExpiredInterceptor } from 'app/core/interceptor/auth-expired.interceptor';
import { authInterceptor } from 'app/core/interceptor/auth.interceptor';
import { errorHandlerInterceptor } from 'app/core/interceptor/error-handler.interceptor';
import { loadingInterceptor } from 'app/core/interceptor/loading.interceptor';
import { notificationInterceptor } from 'app/core/interceptor/notification.interceptor';

import './config/dayjs';
import { TranslationModule } from 'app/shared/language/translation.module';

import { ThemeService } from 'app/core/theme/theme.service';

import { AppPageTitleStrategy } from './app-page-title-strategy';
import routes from './app.routes';
import { NgbDateDayjsAdapter } from './config/datepicker-adapter';
import { NgxSpinnerModule } from 'ngx-spinner';

/** Clé de garde : une seule tentative de rechargement automatique par session. */
const CHUNK_RELOAD_KEY = 'jhi-chunk-reload';

/**
 * Vrai quand la navigation a échoué parce qu'un chunk paresseux n'a pas pu être téléchargé.
 *
 * <p>Ce n'est pas une erreur applicative : le bundle est découpé en dizaines de chunks, et sur un
 * premier accès (nouveau navigateur, mobile, cache froid) il suffit qu'une seule requête échoue —
 * réseau instable, connexion coupée, déploiement pendant le chargement — pour que le routeur lève
 * une erreur de navigation. Le contenu est intact côté serveur : un rechargement suffit.</p>
 */
const isChunkLoadFailure = (error: unknown): boolean => {
  const message = error instanceof Error ? `${error.name} ${error.message}` : typeof error === 'string' ? error : '';
  return /ChunkLoadError|Loading chunk|dynamically imported module|Importing a module script failed/i.test(message);
};

const routerFeatures: RouterFeatures[] = [
  withComponentInputBinding(),
  withNavigationErrorHandler((e: NavigationError) => {
    const router = inject(Router);
    if (isChunkLoadFailure(e.error)) {
      // Rechargement automatique, une seule fois : sans ce garde-fou, un chunk réellement absent
      // (déploiement cassé) boucleraient à l'infini. Au 2e échec, on montre la page d'erreur.
      if (!sessionStorage.getItem(CHUNK_RELOAD_KEY)) {
        sessionStorage.setItem(CHUNK_RELOAD_KEY, String(Date.now()));
        location.reload();
        return;
      }
      router.navigate(['/error']);
      return;
    }
    sessionStorage.removeItem(CHUNK_RELOAD_KEY);
    if (e.error.status === 403) {
      router.navigate(['/accessdenied']);
    } else if (e.error.status === 404) {
      router.navigate(['/404']);
    } else if (e.error.status === 401) {
      router.navigate(['/login']);
    } else {
      router.navigate(['/error']);
    }
  }),
];
if (environment.DEBUG_INFO_ENABLED) {
  routerFeatures.push(withDebugTracing());
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes, ...routerFeatures),
    // Set this to true to enable service worker (PWA)
    provideServiceWorker('ngsw-worker.js', { enabled: false }),
    importProvidersFrom(TranslationModule),
    importProvidersFrom(NgxSpinnerModule.forRoot({ type: 'ball-scale-multiple' })),
    provideHttpClient(
      withInterceptors([loadingInterceptor, authInterceptor, authExpiredInterceptor, errorHandlerInterceptor, notificationInterceptor]),
    ),
    Title,
    { provide: LOCALE_ID, useValue: 'fr' },
    { provide: NgbDateAdapter, useClass: NgbDateDayjsAdapter },
    { provide: TitleStrategy, useClass: AppPageTitleStrategy },
    provideAppInitializer(() => inject(ThemeService).init()),
  ],
};
