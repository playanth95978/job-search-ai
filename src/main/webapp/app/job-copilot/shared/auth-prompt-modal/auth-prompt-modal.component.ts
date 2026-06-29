import { ChangeDetectionStrategy, Component, inject, model } from '@angular/core';
import { Router } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateModule } from '@ngx-translate/core';

import { TranslateDirective } from 'app/shared/language';
import { StateStorageService } from 'app/core/auth/state-storage.service';

/**
 * Invitation modal shown when an anonymous visitor triggers an auth-only action
 * (match my profile, match advanced, apply). Offers register / login; both store
 * the current URL so the visitor returns here after authenticating (and the route
 * guard then steers first-time users through onboarding).
 */
@Component({
  selector: 'jhi-auth-prompt-modal',
  imports: [FaIconComponent, TranslateModule, TranslateDirective],
  templateUrl: './auth-prompt-modal.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AuthPromptModalComponent {
  /** i18n key describing the locked feature, shown in the modal body. */
  featureKey = model<string>('jobCopilot.authPrompt.defaultFeature');

  private readonly activeModal = inject(NgbActiveModal);
  private readonly router = inject(Router);
  private readonly stateStorage = inject(StateStorageService);

  register(): void {
    this.go('/account/register');
  }

  login(): void {
    this.go('/login');
  }

  close(): void {
    this.activeModal.dismiss();
  }

  private go(path: string): void {
    this.stateStorage.storeUrl(this.router.url);
    this.activeModal.close();
    this.router.navigate([path]);
  }
}
