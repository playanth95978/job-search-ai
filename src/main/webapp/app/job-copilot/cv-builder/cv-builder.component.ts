import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { debounceTime, filter } from 'rxjs/operators';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateModule } from '@ngx-translate/core';
import TranslateDirective from 'app/shared/language/translate.directive';

import { ResumeStore } from './resume-store';
import { CvEditorComponent } from './editor/cv-editor.component';
import { CvPreviewComponent } from './preview/cv-preview.component';
import { CvReviewComponent } from './cv-review/cv-review.component';
import { CvMatchComponent } from './cv-match/cv-match.component';
import { CvGenerateComponent } from './cv-generate/cv-generate.component';
import { FONT_FAMILIES, Resume, ResumeTemplateId } from './resume.model';
import { CvResumeApiService } from './cv-resume-api.service';
import { candidateProfileToResume } from './candidate-profile.mapper';
import { generatedResumeToResume } from './generated-resume.mapper';
import { CvExportService } from './cv-export.service';

@Component({
  selector: 'jhi-cv-builder',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    FaIconComponent,
    TranslateModule,
    TranslateDirective,
    CvEditorComponent,
    CvPreviewComponent,
    CvReviewComponent,
    CvMatchComponent,
    CvGenerateComponent,
  ],
  templateUrl: './cv-builder.component.html',
  styleUrl: './cv-builder.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CvBuilderComponent {
  readonly store = inject(ResumeStore);
  private readonly api = inject(CvResumeApiService);
  private readonly exportService = inject(CvExportService);
  readonly r = this.store.resume;
  readonly fonts = FONT_FAMILIES;

  readonly templates: { id: ResumeTemplateId; labelKey: string }[] = [
    { id: 'modern', labelKey: 'cvBuilder.templates.modern' },
    { id: 'professional', labelKey: 'cvBuilder.templates.professional' },
    { id: 'minimalist', labelKey: 'cvBuilder.templates.minimalist' },
    { id: 'australian', labelKey: 'cvBuilder.templates.australian' },
  ];

  readonly translateLanguages = [
    { value: 'French', labelKey: 'jobCopilot.applyModal.languages.french' },
    { value: 'English', labelKey: 'jobCopilot.applyModal.languages.english' },
    { value: 'Spanish', labelKey: 'jobCopilot.applyModal.languages.spanish' },
    { value: 'German', labelKey: 'jobCopilot.applyModal.languages.german' },
  ];
  readonly translationLanguage = signal('English');
  readonly translating = signal(false);

  /** Becomes true once the server copy has been loaded, gating backend auto-save. */
  private readonly loaded = signal(false);

  constructor() {
    // 1. Load the server copy once (overrides the local draft), then enable auto-save.
    this.api
      .getResume()
      .pipe(takeUntilDestroyed())
      .subscribe({
        next: dto => {
          if (dto?.data) {
            try {
              this.store.load(JSON.parse(dto.data) as Resume);
            } catch {
              /* keep local draft on parse error */
            }
          }
          this.loaded.set(true);
        },
        error: () => this.loaded.set(true),
      });

    // 2. Debounced backend auto-save: a single RxJS subscription instead of a per-change effect.
    toObservable(this.store.resume)
      .pipe(
        filter(() => this.loaded()),
        debounceTime(1500),
        takeUntilDestroyed(),
      )
      .subscribe(resume => {
        this.api.saveResume({ title: resume.title, template: resume.template, data: JSON.stringify(resume) }).subscribe();
      });
  }

  exportJson(): void {
    const data = JSON.stringify(this.r(), null, 2);
    this.download(new Blob([data], { type: 'application/json' }), `${this.safeName()}.json`);
  }

  importJson(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      try {
        const parsed = JSON.parse(reader.result as string) as Resume;
        this.store.load(parsed);
      } catch {
        /* ignore invalid file */
      }
      input.value = '';
    };
    reader.readAsText(file);
  }

  exportPdf(): void {
    void this.exportService.exportPdf(this.r(), `${this.safeName()}.pdf`);
  }

  exportDocx(): void {
    void this.exportService.exportDocx(this.r(), `${this.safeName()}.docx`);
  }

  translateCv(): void {
    const current = this.store.resume();
    this.translating.set(true);
    this.api.translateResume(JSON.stringify(current), this.translationLanguage()).subscribe({
      next: dto => {
        const translated = generatedResumeToResume(dto, current);
        this.store.load(translated);
        this.translating.set(false);
      },
      error: () => this.translating.set(false),
    });
  }

  /** Prefills the editor from the user's extracted candidate profile, keeping the chosen template/theme. */
  loadProfile(): void {
    this.api.getCandidateProfile().subscribe(dto => {
      if (!dto) return;
      const current = this.store.resume();
      const mapped = candidateProfileToResume(dto);
      this.store.load({ ...mapped, template: current.template, theme: current.theme });
    });
  }
  private safeName(): string {
    const p = this.r().personal;
    const base = `${p.firstName}-${p.lastName}`.trim() || this.r().title || 'cv';
    return base
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/(^-|-$)/g, '');
  }

  private download(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  }
}
