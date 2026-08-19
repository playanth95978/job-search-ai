import { ChangeDetectionStrategy, Component, inject, output, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateModule } from '@ngx-translate/core';
import { TranslateDirective } from 'app/shared/language';
import { RevealOnScrollDirective } from 'app/shared/reveal/reveal-on-scroll.directive';
import { HttpClient } from '@angular/common/http';

interface LandingItem {
  icon: string;
  titleKey: string;
  descKey: string;
}

const FEATURES: LandingItem[] = [
  { icon: 'search', titleKey: 'home.landing.features.search.title', descKey: 'home.landing.features.search.desc' },
  { icon: 'file-alt', titleKey: 'home.landing.features.coverLetter.title', descKey: 'home.landing.features.coverLetter.desc' },
  { icon: 'user-tie', titleKey: 'home.landing.features.interview.title', descKey: 'home.landing.features.interview.desc' },
  { icon: 'file-arrow-up', titleKey: 'home.landing.features.cv.title', descKey: 'home.landing.features.cv.desc' },
  { icon: 'clipboard-list', titleKey: 'home.landing.features.tracker.title', descKey: 'home.landing.features.tracker.desc' },
  { icon: 'building', titleKey: 'home.landing.features.local.title', descKey: 'home.landing.features.local.desc' },
];

const STEPS: LandingItem[] = [
  { icon: 'file-arrow-up', titleKey: 'home.landing.steps.import.title', descKey: 'home.landing.steps.import.desc' },
  { icon: 'search', titleKey: 'home.landing.steps.discover.title', descKey: 'home.landing.steps.discover.desc' },
  { icon: 'briefcase', titleKey: 'home.landing.steps.apply.title', descKey: 'home.landing.steps.apply.desc' },
];

const TRUST: LandingItem[] = [
  { icon: 'circle-check', titleKey: 'home.landing.trust.free.title', descKey: 'home.landing.trust.free.desc' },
  { icon: 'lock', titleKey: 'home.landing.trust.privacy.title', descKey: 'home.landing.trust.privacy.desc' },
  { icon: 'location-dot', titleKey: 'home.landing.trust.local.title', descKey: 'home.landing.trust.local.desc' },
];

@Component({
  selector: 'jhi-home-landing',
  standalone: true,
  imports: [TranslateDirective, TranslateModule, RouterLink, FaIconComponent, RevealOnScrollDirective],
  templateUrl: './home-landing.html',
  styleUrl: './home-landing.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomeLanding {
  loginAction = output();

  protected readonly features = signal(FEATURES);
  protected readonly steps = signal(STEPS);
  protected readonly trust = signal(TRUST);
  protected readonly http = inject(HttpClient);
  login(): void {
    this.loginAction.emit();
  }

  loginWithGoogle(): void {
    this.http.get<{ url: string }>('/api/google-drive/oauth/login-url').subscribe({
      next({ url }) {
        window.location.href = url;
      },
      error() {
        // fallback direct redirect si l'endpoint échoue
        window.location.href = '/api/google-drive/oauth/login';
      },
    });
  }
}
