import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import {
  Document,
  Paragraph,
  TextRun,
  HeadingLevel,
  BorderStyle,
  AlignmentType,
  Packer,
  Table,
  TableRow,
  TableCell,
  WidthType,
  ShadingType,
  convertInchesToTwip,
} from 'docx';
import { Resume } from './resume.model';

interface DocxSectionLabels {
  summary: string;
  experiences: string;
  education: string;
  skills: string;
  languages: string;
  projects: string;
  certifications: string;
  referees: string;
}

const DOCX_LABELS_FR: DocxSectionLabels = {
  summary: 'Profil',
  experiences: 'Expérience',
  education: 'Formation',
  skills: 'Compétences',
  languages: 'Langues',
  projects: 'Projets',
  certifications: 'Certifications',
  referees: 'Référents',
};

const DOCX_LABELS_EN: DocxSectionLabels = {
  summary: 'Professional Summary',
  experiences: 'Professional Experience',
  education: 'Education & Qualifications',
  skills: 'Key Skills',
  languages: 'Languages',
  projects: 'Projects',
  certifications: 'Certifications & Licences',
  referees: 'Referees',
};

@Injectable({ providedIn: 'root' })
export class CvExportService {
  private readonly http = inject(HttpClient);

  /**
   * Renders the PDF server-side (XHTML + print CSS → openhtmltopdf) and downloads the result.
   *
   * This used to screenshot the `.cv-page` element with html2canvas and wrap the bitmap in a jsPDF
   * document. The resulting "text" was an image: not selectable, not searchable, and unreadable by the
   * ATS parsers this product exists to get past. It also sliced the canvas at a fixed height when the
   * CV ran past one page, cutting lines in half. The server emits real text and breaks pages on entry
   * boundaries.
   *
   * The current editor state is posted rather than rendered from the stored resume, because auto-save
   * is debounced by 1.5s — the server copy can be one edit behind what the user is looking at.
   */
  async exportPdf(resume: Resume, filename: string): Promise<void> {
    const blob = await firstValueFrom(this.http.post('api/cv-builder/resume/pdf', resume, { responseType: 'blob' }));
    this.triggerDownload(blob, filename);
  }

  /** Generates a structured DOCX from the resume data. */
  async exportDocx(resume: Resume, filename: string): Promise<void> {
    const primary = resume.theme.primaryColor ?? '#2563eb';
    const primaryHex = primary.replace('#', '');

    // The Australian template uses English headings and conventions; keep the DOCX consistent with it.
    const isAustralian = resume.template === 'australian';
    const labels = isAustralian ? DOCX_LABELS_EN : DOCX_LABELS_FR;
    const presentLabel = isAustralian ? 'Present' : "Aujourd'hui";

    const sections = resume.sectionOrder.filter(s => !resume.hiddenSections.includes(s));

    const children: (Paragraph | Table)[] = [];

    // ── Header ────────────────────────────────────────────────────────────────
    const { firstName, lastName, title: jobTitle, email, phone, location, linkedin, github, website, workingRights } = resume.personal;
    children.push(
      new Paragraph({
        text: `${firstName} ${lastName}`.trim(),
        heading: HeadingLevel.TITLE,
        alignment: AlignmentType.CENTER,
      }),
    );
    if (jobTitle) {
      children.push(new Paragraph({ text: jobTitle, alignment: AlignmentType.CENTER }));
    }

    const contactParts = [email, phone, location, linkedin, github, website].filter(Boolean);
    if (contactParts.length) {
      children.push(
        new Paragraph({
          alignment: AlignmentType.CENTER,
          children: contactParts.map(
            (part, i) =>
              new TextRun({
                text: i < contactParts.length - 1 ? `${part}  |  ` : part,
                size: 20,
                color: '555555',
              }),
          ),
        }),
      );
    }

    if (workingRights) {
      children.push(
        new Paragraph({
          alignment: AlignmentType.CENTER,
          children: [new TextRun({ text: workingRights, size: 20, italics: true, color: '555555' })],
        }),
      );
    }

    children.push(new Paragraph({ text: '' }));

    // ── Sections ──────────────────────────────────────────────────────────────
    for (const section of sections) {
      switch (section) {
        case 'summary':
          if (resume.summary) {
            children.push(...this.sectionHeader(labels.summary, primaryHex));
            children.push(new Paragraph({ text: resume.summary }));
            children.push(new Paragraph({ text: '' }));
          }
          break;

        case 'experiences':
          if (resume.experiences.length) {
            children.push(...this.sectionHeader(labels.experiences, primaryHex));
            for (const exp of resume.experiences) {
              children.push(
                new Paragraph({
                  children: [
                    new TextRun({ text: exp.jobTitle, bold: true }),
                    new TextRun({
                      text: `  —  ${exp.startDate} › ${exp.current ? presentLabel : exp.endDate}`,
                      color: '888888',
                      size: 20,
                    }),
                  ],
                }),
              );
              if (exp.company) {
                children.push(new Paragraph({ text: exp.company, style: 'IntenseQuote' }));
              }
              if (exp.description) {
                children.push(new Paragraph({ text: exp.description }));
              }
              for (const achievement of exp.achievements) {
                children.push(new Paragraph({ text: achievement, bullet: { level: 0 } }));
              }
              children.push(new Paragraph({ text: '' }));
            }
          }
          break;

        case 'education':
          if (resume.education.length) {
            children.push(...this.sectionHeader(labels.education, primaryHex));
            for (const ed of resume.education) {
              children.push(
                new Paragraph({
                  children: [
                    new TextRun({ text: ed.degree, bold: true }),
                    new TextRun({
                      text: `  —  ${ed.startDate} › ${ed.endDate}`,
                      color: '888888',
                      size: 20,
                    }),
                  ],
                }),
              );
              if (ed.school) {
                children.push(new Paragraph({ text: ed.school }));
              }
              if (ed.description) {
                children.push(new Paragraph({ text: ed.description }));
              }
              children.push(new Paragraph({ text: '' }));
            }
          }
          break;

        case 'skills':
          if (resume.skills.length) {
            children.push(...this.sectionHeader(labels.skills, primaryHex));
            children.push(
              new Paragraph({
                children: resume.skills.map(
                  (sk, i) =>
                    new TextRun({
                      text: i < resume.skills.length - 1 ? `${sk.name}  ·  ` : sk.name,
                    }),
                ),
              }),
            );
            children.push(new Paragraph({ text: '' }));
          }
          break;

        case 'languages':
          if (resume.languages.length) {
            children.push(...this.sectionHeader(labels.languages, primaryHex));
            children.push(
              new Paragraph({
                children: resume.languages.map(
                  (l, i) =>
                    new TextRun({
                      text: i < resume.languages.length - 1 ? `${l.name} (${l.level})  ·  ` : `${l.name} (${l.level})`,
                    }),
                ),
              }),
            );
            children.push(new Paragraph({ text: '' }));
          }
          break;

        case 'projects':
          if (resume.projects.length) {
            children.push(...this.sectionHeader(labels.projects, primaryHex));
            for (const proj of resume.projects) {
              children.push(
                new Paragraph({
                  children: [
                    new TextRun({ text: proj.name, bold: true }),
                    proj.url ? new TextRun({ text: `  —  ${proj.url}`, color: '888888', size: 20 }) : new TextRun(''),
                  ],
                }),
              );
              if (proj.description) {
                children.push(new Paragraph({ text: proj.description }));
              }
              if (proj.technologies.length) {
                children.push(
                  new Paragraph({
                    children: [
                      new TextRun({ text: 'Technologies : ', italics: true, size: 20 }),
                      new TextRun({ text: proj.technologies.join(', '), size: 20, color: '555555' }),
                    ],
                  }),
                );
              }
              children.push(new Paragraph({ text: '' }));
            }
          }
          break;

        case 'certifications':
          if (resume.certifications.length) {
            children.push(...this.sectionHeader(labels.certifications, primaryHex));
            for (const cert of resume.certifications) {
              children.push(
                new Paragraph({
                  children: [
                    new TextRun({ text: cert.name, bold: true }),
                    new TextRun({ text: `  —  ${cert.issuer}  ·  ${cert.date}`, color: '888888', size: 20 }),
                  ],
                }),
              );
            }
            children.push(new Paragraph({ text: '' }));
          }
          break;
      }
    }

    // ── Referees (Australian convention) ───────────────────────────────────────
    if (isAustralian) {
      children.push(...this.sectionHeader(labels.referees, primaryHex));
      if (resume.referees.length) {
        for (const ref of resume.referees) {
          children.push(new Paragraph({ children: [new TextRun({ text: ref.name, bold: true })] }));
          const role = [ref.position, ref.company].filter(Boolean).join(', ');
          if (role) {
            children.push(new Paragraph({ text: role }));
          }
          const refContact = [ref.email, ref.phone].filter(Boolean).join('  ·  ');
          if (refContact) {
            children.push(new Paragraph({ children: [new TextRun({ text: refContact, size: 20, color: '555555' })] }));
          }
          children.push(new Paragraph({ text: '' }));
        }
      } else {
        children.push(new Paragraph({ children: [new TextRun({ text: 'Available upon request.', italics: true, color: '555555' })] }));
      }
    }

    const doc = new Document({
      creator: `${firstName} ${lastName}`.trim(),
      title: resume.title || 'CV',
      sections: [{ children }],
    });

    const blob = await Packer.toBlob(doc);
    this.triggerDownload(blob, filename);
  }

  private sectionHeader(label: string, colorHex: string): Paragraph[] {
    return [
      new Paragraph({
        text: label.toUpperCase(),
        heading: HeadingLevel.HEADING_2,
        border: {
          bottom: { style: BorderStyle.SINGLE, size: 6, color: colorHex },
        },
        spacing: { before: convertInchesToTwip(0.05), after: convertInchesToTwip(0.05) },
      }),
    ];
  }

  private triggerDownload(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  }
}
