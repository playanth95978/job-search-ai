import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Experience, experienceCompanyLine, Resume, SectionKey } from '../resume.model';

/**
 * Australian-style resume: a conservative, ATS-friendly single-column layout. Follows local
 * conventions — no photo/DOB, English section headings, key skills as a bulleted grid, and the
 * signature "Referees — available upon request" footer. Honours sectionOrder / hiddenSections
 * like the other templates and themes via the shared --cv-* custom properties.
 */
@Component({
  selector: 'jhi-cv-tmpl-australian',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './template-australian.component.html',
  styleUrl: './template-australian.component.scss',
})
export class CvTemplateAustralianComponent {
  readonly resume = input.required<Resume>();
  readonly sections = computed<SectionKey[]>(() => {
    const r = this.resume();
    return r.sectionOrder.filter(s => !r.hiddenSections.includes(s));
  });
  /** Employer + role location on one line; empty when neither is filled in. */
  companyLine(e: Experience): string {
    return experienceCompanyLine(e);
  }

  readonly contactLine = computed(() => {
    const p = this.resume().personal;
    // Pipe, not a middle dot: parsers split contact lines on conventional delimiters and "|" is in
    // every list of them. Matches the PDF and DOCX exports.
    return [p.email, p.phone, p.location, p.linkedin, p.github, p.website].filter(Boolean).join('  |  ');
  });
}
