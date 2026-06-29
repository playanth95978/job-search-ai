import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { Resume } from '../resume.model';
import { CvTemplateModernComponent } from './template-modern.component';
import { CvTemplateProfessionalComponent } from './template-professional.component';
import { CvTemplateMinimalistComponent } from './template-minimalist.component';
import { CvTemplateAustralianComponent } from './template-australian.component';

/**
 * Renders the live CV. Theme (colors/font/size) is applied via CSS custom properties on the
 * page root, so any change in the store updates the preview instantly. The template is swapped
 * by binding the same {@link Resume} model to one of the standalone template components.
 */
@Component({
  selector: 'jhi-cv-preview',
  standalone: true,
  imports: [CvTemplateModernComponent, CvTemplateProfessionalComponent, CvTemplateMinimalistComponent, CvTemplateAustralianComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let r = resume();
    <div
      class="cv-page"
      [style.--cv-primary]="r.theme.primaryColor"
      [style.--cv-secondary]="r.theme.secondaryColor"
      [style.--cv-font]="r.theme.fontFamily"
      [style.font-family]="r.theme.fontFamily"
      [style.font-size.px]="r.theme.fontSize"
    >
      @switch (r.template) {
        @case ('professional') {
          <jhi-cv-tmpl-professional [resume]="r" />
        }
        @case ('minimalist') {
          <jhi-cv-tmpl-minimalist [resume]="r" />
        }
        @case ('australian') {
          <jhi-cv-tmpl-australian [resume]="r" />
        }
        @default {
          <jhi-cv-tmpl-modern [resume]="r" />
        }
      }
    </div>
  `,
  styles: `
    :host {
      display: block;
    }
    .cv-page {
      background: #fff;
      color: #1f2733;
      width: 100%;
      max-width: 794px; /* ~A4 width at 96dpi */
      min-height: 1123px; /* ~A4 height */
      margin: 0 auto;
      box-shadow: 0 6px 24px rgba(0, 0, 0, 0.12);
      border-radius: 8px;
      overflow: hidden;
    }
  `,
})
export class CvPreviewComponent {
  readonly resume = input.required<Resume>();
  // exposed for potential parent logic (e.g. export) without re-reading the store
  readonly template = computed(() => this.resume().template);
}
