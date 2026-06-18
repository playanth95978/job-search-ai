import { Injectable } from '@angular/core';
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

@Injectable({ providedIn: 'root' })
export class CvExportService {
  /**
   * Captures the rendered .cv-page element via html2canvas and generates a faithful PDF via jsPDF.
   * Dynamic import keeps these heavy libs out of the initial bundle.
   */
  async exportPdf(cvPageElement: HTMLElement, filename: string): Promise<void> {
    const [{ default: html2canvas }, { default: jsPDF }] = await Promise.all([import('html2canvas'), import('jspdf')]);

    // Scale 2x for crisp rendering on high-DPI screens
    const canvas = await html2canvas(cvPageElement, {
      scale: 2,
      useCORS: true,
      backgroundColor: '#ffffff',
      logging: false,
    });

    const imgData = canvas.toDataURL('image/png');
    // A4: 210 × 297 mm
    const pdf = new jsPDF({ orientation: 'portrait', unit: 'mm', format: 'a4' });
    const pageW = pdf.internal.pageSize.getWidth();
    const pageH = pdf.internal.pageSize.getHeight();

    const canvasAspect = canvas.height / canvas.width;
    const imgH = pageW * canvasAspect;

    if (imgH <= pageH) {
      pdf.addImage(imgData, 'PNG', 0, 0, pageW, imgH);
    } else {
      // Multi-page: slice the canvas height-wise
      let yOffset = 0;
      const sliceH = Math.floor((canvas.width * pageH) / pageW);
      while (yOffset < canvas.height) {
        const slice = document.createElement('canvas');
        slice.width = canvas.width;
        slice.height = Math.min(sliceH, canvas.height - yOffset);
        const ctx = slice.getContext('2d')!;
        ctx.drawImage(canvas, 0, -yOffset);
        if (yOffset > 0) pdf.addPage();
        pdf.addImage(slice.toDataURL('image/png'), 'PNG', 0, 0, pageW, (slice.height / canvas.width) * pageW);
        yOffset += sliceH;
      }
    }

    pdf.save(filename);
  }

  /** Generates a structured DOCX from the resume data. */
  async exportDocx(resume: Resume, filename: string): Promise<void> {
    const primary = resume.theme.primaryColor ?? '#2563eb';
    const primaryHex = primary.replace('#', '');

    const sections = resume.sectionOrder.filter(s => !resume.hiddenSections.includes(s));

    const children: (Paragraph | Table)[] = [];

    // ── Header ────────────────────────────────────────────────────────────────
    const { firstName, lastName, title: jobTitle, email, phone, location, linkedin, github, website } = resume.personal;
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

    children.push(new Paragraph({ text: '' }));

    // ── Sections ──────────────────────────────────────────────────────────────
    for (const section of sections) {
      switch (section) {
        case 'summary':
          if (resume.summary) {
            children.push(...this.sectionHeader('Profil', primaryHex));
            children.push(new Paragraph({ text: resume.summary }));
            children.push(new Paragraph({ text: '' }));
          }
          break;

        case 'experiences':
          if (resume.experiences.length) {
            children.push(...this.sectionHeader('Expérience', primaryHex));
            for (const exp of resume.experiences) {
              children.push(
                new Paragraph({
                  children: [
                    new TextRun({ text: exp.jobTitle, bold: true }),
                    new TextRun({
                      text: `  —  ${exp.startDate} › ${exp.current ? "Aujourd'hui" : exp.endDate}`,
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
            children.push(...this.sectionHeader('Formation', primaryHex));
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
            children.push(...this.sectionHeader('Compétences', primaryHex));
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
            children.push(...this.sectionHeader('Langues', primaryHex));
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
            children.push(...this.sectionHeader('Projets', primaryHex));
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
            children.push(...this.sectionHeader('Certifications', primaryHex));
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
