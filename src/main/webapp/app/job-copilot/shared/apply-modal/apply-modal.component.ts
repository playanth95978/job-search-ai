import { ChangeDetectionStrategy, Component, computed, inject, model, OnInit, output, signal } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { NgbActiveModal, NgbNavModule } from '@ng-bootstrap/ng-bootstrap';

import { JobCopilotService } from '../job-copilot.service';
import { JobApplication, JobOffer, JobOfferPreview, jobSourceColor, jobSourceLabel } from '../job-copilot.model';
import TranslateDirective from '../../../shared/language/translate.directive';

import { ApplyPositionComponent } from './components/apply-position/apply-position.component';
import { ApplyTailoredComponent } from './components/apply-tailored/apply-tailored.component';
import { ApplyOfferComponent } from './components/apply-offer/apply-offer.component';
import { ApplyCoverComponent } from './components/apply-cover/apply-cover.component';
import { ApplyPrefillComponent } from './components/apply-prefill/apply-prefill.component';

@Component({
  selector: 'jhi-apply-modal',
  standalone: true,
  imports: [
    FaIconComponent,
    TranslatePipe,
    TranslateDirective,
    NgbNavModule,
    ApplyPositionComponent,
    ApplyTailoredComponent,
    ApplyOfferComponent,
    ApplyCoverComponent,
    ApplyPrefillComponent,
  ],
  templateUrl: './apply-modal.component.html',
  styleUrl: './apply-modal.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ApplyModalComponent implements OnInit {
  /** Provide either an existing draft application... */
  existingApplication = model<JobApplication | undefined>();
  /** ...or a job offer to create a new draft application for. */
  jobOffer = model<JobOfferPreview | undefined>();
  jobOfferId = model<string | undefined>();
  closed = output<void>();
  applied = output<JobApplication>();
  /** Active NgbNav tab. */
  activeTab = signal<'position' | 'offer' | 'cover'>('position');
  application = signal<JobApplication | null>(null);
  coverLetter = signal('');
  loading = signal(true);
  submitting = signal(false);
  error = signal<string | null>(null);
  resultMessage = signal<string | null>(null);
  language = signal('');
  /** True while the full description is being fetched from the source. */
  enriching = signal(false);
  /** Emits the enriched offer so the parent list can be refreshed. */
  enrichedOffer = output<JobOffer>();

  private service = inject(JobCopilotService);
  private translateService = inject(TranslateService);
  private activeModal = inject(NgbActiveModal);

  ngOnInit(): void {
    this.enrichOffer();

    if (this.existingApplication()) {
      this.application.set(this.existingApplication()!);
      this.coverLetter.set(this.existingApplication()?.coverLetter ?? '');
      this.loading.set(false);
    } else if (this.jobOfferId()) {
      this.service.createApplication(this.jobOfferId()!, true, this.language() || undefined).subscribe({
        next: app => {
          this.application.set(app);
          this.coverLetter.set(app.coverLetter ?? '');
          this.loading.set(false);
        },
        error: err => {
          this.error.set(err.error?.message ?? this.translateService.instant('jobCopilot.applyModal.error.create'));
          this.loading.set(false);
        },
      });
    } else {
      this.loading.set(false);
      this.error.set(this.translateService.instant('jobCopilot.applyModal.error.noOffer'));
    }
  }

  offer = computed(() => this.application()?.jobOffer ?? this.jobOffer());

  sourceLabel = computed(() => jobSourceLabel(this.offer()?.source));

  sourceColor = computed(() => jobSourceColor(this.offer()?.source));

  applyUrl = computed(() => this.offer()?.applyUrl);

  offerId = computed(() => this.jobOfferId() ?? this.application()?.jobOffer?.id);

  /** Candidate identity used to pre-fill the source application form (copy-to-clipboard fields). */
  candidate = computed(() => this.application()?.candidateProfile ?? this.existingApplication()?.candidateProfile);

  /**
   * Candidature assistée — le seul mode de candidature de l'application. On copie la lettre dans le
   * presse-papier, on ouvre l'offre dans un onglet, et on enregistre la candidature comme APPLIED
   * pour le suivi. C'est le candidat qui valide et soumet le formulaire chez la source : rien n'est
   * jamais envoyé en son nom.
   */
  applyAssisted(): void {
    const url = this.applyUrl();
    if (this.coverLetter()) {
      void navigator.clipboard.writeText(this.coverLetter());
    }
    if (url) {
      window.open(url, '_blank', 'noopener');
    }
    this.markAsApplied();
  }

  markAsApplied(): void {
    const app = this.application();
    if (!app?.id) return;
    this.submitting.set(true);
    this.error.set(null);
    this.service.updateCoverLetter(app.id, this.coverLetter()).subscribe({
      next: () => {
        this.service.updateApplicationStatus(app.id!, 'APPLIED').subscribe({
          next: updated => {
            this.submitting.set(false);
            this.application.set(updated);
            this.resultMessage.set(this.translateService.instant('jobCopilot.applyModal.success.markedApplied'));
            this.applied.emit(updated);
            this.activeModal.close(updated);
          },
          error: err => {
            this.submitting.set(false);
            this.error.set(err.error?.message ?? this.translateService.instant('jobCopilot.applyModal.error.status'));
          },
        });
      },
      error: err => {
        this.submitting.set(false);
        this.error.set(err.error?.message ?? this.translateService.instant('jobCopilot.applyModal.error.save'));
      },
    });
  }

  close(): void {
    this.closed.emit();
    this.activeModal.dismiss();
  }

  /** Lazily fetches the full description (one request) and queues the offer for embedding. */
  private enrichOffer(): void {
    const id = this.jobOfferId();
    if (!id) return;
    this.enriching.set(true);
    this.service.enrichJob(id).subscribe({
      next: full => {
        this.jobOffer.update(current => ({ ...current, ...full }));
        this.enrichedOffer.emit(full);
        this.enriching.set(false);
      },
      error: () => this.enriching.set(false),
    });
  }
}
