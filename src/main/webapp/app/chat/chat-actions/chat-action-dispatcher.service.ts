import { inject, Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';

import { JobCopilotService } from '../../job-copilot/shared/job-copilot.service';
import { MessageService } from '../message.service';
import { ChatAction, ChatMutateAction, isSafeRoute } from './chat-action.model';

/**
 * Executes a chat action clicked in the thread.
 *
 * Lives in a service rather than in each container because the full `/chat` page and the floating
 * widget offer the same buttons: duplicating the dispatch would let the two drift apart, and a
 * mutating action is not something to implement twice.
 *
 * {@link ChatActionsComponent} stays purely presentational — it emits the action, this decides what
 * it does.
 */
@Injectable({ providedIn: 'root' })
export class ChatActionDispatcherService {
  private readonly router = inject(Router);
  private readonly messageService = inject(MessageService);
  private readonly jobCopilotService = inject(JobCopilotService);
  private readonly translate = inject(TranslateService);

  /**
   * Runs `action`.
   *
   * The switch is exhaustive over the union, so adding a variant to the contract makes this fail to
   * compile rather than silently render a button that does nothing.
   */
  dispatch(action: ChatAction): void {
    switch (action.type) {
      case 'prompt':
        // `useSuggestion` and not `sendSuggestion`: the backend already sends a ready sentence,
        // whereas `sendSuggestion` expects an i18n key to translate first.
        this.messageService.useSuggestion(action.text);
        break;
      case 'navigate':
        this.navigate(action.route);
        break;
      case 'mutate':
        this.mutate(action);
        break;
    }
  }

  /**
   * Opens an internal route, refusing anything else.
   *
   * The backend is trusted today and its own tests assert the routes stay internal, but this value
   * drives `navigateByUrl`: a redirect out of the application must not hinge on that trust alone.
   */
  private navigate(route: string): void {
    if (!isSafeRoute(route)) {
      // eslint-disable-next-line no-console
      console.warn('Chat action route refusée (hors application) :', route);
      return;
    }
    void this.router.navigateByUrl(route);
  }

  private mutate(action: ChatMutateAction): void {
    if (!action.offerId) {
      return;
    }
    // The confirmation is the backend's call, not this service's: `APPLY` always carries
    // `confirm: true` because the application leaves for the recruiter.
    if (action.confirm && !this.confirmed(action)) {
      return;
    }

    switch (action.operation) {
      case 'GENERATE_COVER_LETTER':
        this.jobCopilotService.generateCoverLetter(action.offerId).subscribe({
          next: () => this.router.navigateByUrl('/job-copilot/applications'),
        });
        break;
      case 'APPLY':
        this.jobCopilotService.createApplication(action.offerId).subscribe({
          next: () => this.router.navigateByUrl('/job-copilot/applications'),
        });
        break;
    }
  }

  /**
   * Asks the user to validate an irreversible action.
   *
   * `window.confirm` rather than a modal: the application has no shared confirmation dialog, and
   * introducing one for two buttons would be more UI than the feature warrants. Swapping it later
   * only touches this method.
   */
  private confirmed(action: ChatMutateAction): boolean {
    return window.confirm(this.translate.instant(`chat.action.confirm.${action.operation}`));
  }
}
