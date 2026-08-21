import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { debounceTime, filter, switchMap } from 'rxjs/operators';
import { of } from 'rxjs';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateModule } from '@ngx-translate/core';
import { NgbNavModule } from '@ng-bootstrap/ng-bootstrap';
import TranslateDirective from 'app/shared/language/translate.directive';

import { ResumeStore } from './resume-store';
import { CvEditorComponent } from './editor/cv-editor.component';
import { CvPreviewComponent } from './preview/cv-preview.component';
import { CvReviewComponent } from './cv-review/cv-review.component';
import { CvMatchComponent } from './cv-match/cv-match.component';
import { CvGenerateComponent } from './cv-generate/cv-generate.component';
import { FONT_FAMILIES, isBlankResume, Resume, ResumeTemplateId } from './resume.model';
import { CvResumeApiService, ResumeApiDto } from './cv-resume-api.service';
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
    NgbNavModule,
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

  /**
   * State of the DATABASE save, which is what the toolbar reports. The store's own draft indicator only
   * ever meant "written to localStorage" — showing it as "saved" is what hid failing saves until now.
   */
  readonly saveState = signal<'idle' | 'saving' | 'saved' | 'error'>('idle');

  /** Server message from the last failed save, shown next to the retry button. */
  readonly saveError = signal<string | null>(null);

  /** Sections the draft holds that the server does not, straight from the store's diff. */
  readonly pendingChanges = computed(() => this.store.pendingChanges());

  constructor() {
    // 1. Load the server copy once (overrides the local draft), then enable auto-save.
    this.api
      .getResume()
      .pipe(
        switchMap(dto => {
          const stored = this.parseStoredResume(dto);
          if (stored) {
            this.store.load(stored);
            // The server copy is, by definition, what the server holds: the diff starts empty.
            this.store.markPersisted(this.store.resume());
            return of(null);
          }
          // No resume saved yet — the common case right after a first CV upload. Seed the builder from
          // the profile that was extracted from that CV instead of showing an empty form, which is
          // otherwise only reachable through the "import profile" button most users never find.
          return this.api.getCandidateProfile();
        }),
        takeUntilDestroyed(),
      )
      .subscribe({
        next: profile => {
          // A local draft with real content in it wins: the user's own typing is never clobbered.
          if (profile && isBlankResume(this.store.resume())) {
            // Only reached when the resume is blank, so there is nothing to preserve here.
            const current = this.store.resume();
            this.store.load({ ...candidateProfileToResume(profile), template: current.template, theme: current.theme });
          }
          this.loaded.set(true);
        },
        error: () => this.loaded.set(true),
      });

    // 2. Debounced backend auto-save: a single RxJS subscription instead of a per-change effect.
    // Only fires when the draft actually diverges from the server copy, so loading does not echo a save
    // straight back, and the result is always reported — a silent .subscribe() is how a failing save
    // ends up looking like a successful one.
    toObservable(this.store.resume)
      .pipe(
        filter(() => this.loaded() && this.store.hasPendingChanges()),
        debounceTime(1500),
        takeUntilDestroyed(),
      )
      .subscribe(resume => this.saveToServer(resume));
  }

  /** Pushes the draft to the database and records the outcome. */
  private saveToServer(resume: Resume): void {
    this.saveState.set('saving');
    this.api.saveResume({ title: resume.title, template: resume.template, data: JSON.stringify(resume) }).subscribe({
      next: () => {
        // Mark the exact resume that was sent, not the current draft: the user may have typed since.
        this.store.markPersisted(resume);
        this.saveState.set('saved');
        this.saveError.set(null);
      },
      error: err => {
        this.saveState.set('error');
        this.saveError.set(err.error?.message ?? err.message ?? null);
      },
    });
  }

  /** Manual retry after a failed auto-save. */
  saveNow(): void {
    this.saveToServer(this.store.resume());
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
  /**
   * Prefills the editor from the user's extracted candidate profile, keeping the chosen template/theme
   * AND the sections the profile cannot know about.
   *
   * <p>The candidate profile has no projects and no referees — nothing extracts them from a CV today — so
   * mapping it over the resume wholesale silently deleted whatever the user had typed there. Those
   * sections are carried across instead: an import enriches the CV, it does not amputate it.
   */
  loadProfile(): void {
    this.api.getCandidateProfile().subscribe(dto => {
      if (!dto) return;
      const current = this.store.resume();
      const mapped = candidateProfileToResume(dto);
      this.store.load({
        ...mapped,
        template: current.template,
        theme: current.theme,
        projects: current.projects,
        referees: current.referees,
        sectionOrder: current.sectionOrder,
        hiddenSections: current.hiddenSections,
      });
    });
  }
  /** The stored resume, or null when there is none or it fails to parse (a corrupt copy keeps the draft). */
  private parseStoredResume(dto: ResumeApiDto | null): Resume | null {
    if (!dto?.data) {
      return null;
    }
    try {
      return JSON.parse(dto.data) as Resume;
    } catch {
      return null;
    }
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
