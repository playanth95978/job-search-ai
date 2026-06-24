import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable, of, switchMap } from 'rxjs';

@Component({
  selector: 'jhi-apply-tailored',
  standalone: true,
  imports: [CommonModule, FaIconComponent, TranslatePipe, TranslateDirective],
  templateUrl: './apply-tailored.component.html',
  styleUrl: './apply-tailored.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ApplyTailoredComponent {
  offerId = input.required<string | undefined>();

  tailored = signal<GeneratedResume | null>(null);
  tailoringLoading = signal(false);
  tailoringError = signal<string | null>(null);
  openingBuilder = signal(false);

  tailoredTopSkills = computed(() => (this.tailored()?.skills ?? []).slice(0, 12));

  private service = inject(JobCopilotService);
  private translateService = inject(TranslateService);
  private cvApi = inject(CvResumeApiService);
  private exportService = inject(CvExportService);
  private router = inject(Router);
  private activeModal = inject(NgbActiveModal);

  generateTailored(refresh = false): void {
    const id = this.offerId();
    if (!id || this.tailoringLoading()) return;
    this.tailoringLoading.set(true);
    this.tailoringError.set(null);
    this.service.getTailoredResume(id, refresh).subscribe({
      next: result => {
        this.tailored.set(result);
        this.tailoringLoading.set(false);
      },
      error: err => {
        this.tailoringError.set(err.error?.message ?? this.translateService.instant('jobCopilot.tailoring.error'));
        this.tailoringLoading.set(false);
      },
    });
  }

  openTailoredInBuilder(): void {
    const tailored = this.tailored();
    if (!tailored || this.openingBuilder()) return;
    this.openingBuilder.set(true);
    this.cvApi
      .getResume()
      .pipe(
        switchMap(current => {
          const base: Resume = current?.data ? (JSON.parse(current.data) as Resume) : createEmptyResume();
          const mapped = generatedResumeToResume(tailored, base);
          const snapshot$: Observable<unknown> = current ? this.cvApi.createVersion() : of(null);
          return snapshot$.pipe(
            switchMap(() => this.cvApi.saveResume({ title: mapped.title, template: mapped.template, data: JSON.stringify(mapped) })),
          );
        }),
      )
      .subscribe({
        next: () => {
          this.openingBuilder.set(false);
          this.activeModal.close();
          this.router.navigate(['/job-copilot/cv-builder']);
        },
        error: err => {
          this.openingBuilder.set(false);
          this.tailoringError.set(err.error?.message ?? this.translateService.instant('jobCopilot.tailoring.error'));
        },
      });
  }

  exportTailoredDocx(): void {
    const tailored = this.tailored();
    if (!tailored) return;
    const resume = generatedResumeToResume(tailored, createEmptyResume());
    const name = `${resume.personal.firstName}-${resume.personal.lastName}`.trim() || 'cv';
    const slug = name
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/(^-|-$)/g, '');
    void this.exportService.exportDocx(resume, `${slug || 'cv'}-tailored.docx`);
  }
}
