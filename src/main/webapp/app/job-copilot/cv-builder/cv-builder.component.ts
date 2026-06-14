import { ChangeDetectionStrategy, Component, effect, inject, signal, untracked } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateModule } from '@ngx-translate/core';
import TranslateDirective from 'app/shared/language/translate.directive';

import { ResumeStore } from './resume-store';
import { CvEditorComponent } from './editor/cv-editor.component';
import { CvPreviewComponent } from './preview/cv-preview.component';
import { FONT_FAMILIES, Resume, ResumeTemplateId } from './resume.model';
import { CvResumeApiService } from './cv-resume-api.service';
import { candidateProfileToResume } from './candidate-profile.mapper';

@Component({
  selector: 'jhi-cv-builder',
  standalone: true,
  imports: [CommonModule, FormsModule, FaIconComponent, TranslateModule, TranslateDirective, CvEditorComponent, CvPreviewComponent],
  templateUrl: './cv-builder.component.html',
  styleUrl: './cv-builder.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CvBuilderComponent {
  readonly store = inject(ResumeStore);
  private readonly api = inject(CvResumeApiService);
  readonly r = this.store.resume;
  readonly fonts = FONT_FAMILIES;

  private readonly resumeResource = rxResource({
    stream: () => this.api.getResume(),
  });


  readonly templates: { id: ResumeTemplateId; labelKey: string }[] = [
    { id: 'modern', labelKey: 'cvBuilder.templates.modern' },
    { id: 'professional', labelKey: 'cvBuilder.templates.professional' },
    { id: 'minimalist', labelKey: 'cvBuilder.templates.minimalist' },
  ];

  /** Becomes true once the server copy has been loaded, gating backend auto-save. */
  private readonly loaded = signal(false);

  constructor() {
    // Overwrite local draft with server copy when it arrives
    effect(() => {
      const res = this.resumeResource.value();
      if (res !== undefined) {
        if (res?.data) {
          try {
            this.store.load(JSON.parse(res.data) as Resume);
          } catch {
            /* ignore parse error, keep local draft */
          }
        }
        this.loaded.set(true);
      }
    });

    effect(onCleanup => {
      const resume = this.store.resume();
      if (!this.loaded()) return;

      const timer = setTimeout(() => {
        untracked(() => {
          this.api.saveResume({ title: resume.title, template: resume.template, data: JSON.stringify(resume) }).subscribe();
        });
      }, 1500);

      onCleanup(() => clearTimeout(timer));
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

  /** Interim PDF export via the browser print dialog (see roadmap for exact Puppeteer rendering). */
  print(): void {
    window.print();
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
