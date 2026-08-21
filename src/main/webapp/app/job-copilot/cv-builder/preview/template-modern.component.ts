import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Experience, Resume, SectionKey, experienceCompanyLine } from '../resume.model';

@Component({
  selector: 'jhi-cv-tmpl-modern',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let r = resume();
    <div class="m-cv">
      <header class="m-head">
        <h1>{{ r.personal.firstName }} {{ r.personal.lastName }}</h1>
        @if (r.personal.title) {
          <p class="m-title">{{ r.personal.title }}</p>
        }
        <ul class="m-contact">
          @if (r.personal.email) {
            <li>{{ r.personal.email }}</li>
          }
          @if (r.personal.phone) {
            <li>{{ r.personal.phone }}</li>
          }
          @if (r.personal.location) {
            <li>{{ r.personal.location }}</li>
          }
          @if (r.personal.linkedin) {
            <li>{{ r.personal.linkedin }}</li>
          }
          @if (r.personal.github) {
            <li>{{ r.personal.github }}</li>
          }
          @if (r.personal.website) {
            <li>{{ r.personal.website }}</li>
          }
        </ul>
      </header>

      <div class="m-body">
        <main class="m-main">
          @for (s of sections(); track s) {
            @switch (s) {
              @case ('summary') {
                @if (r.summary) {
                  <section>
                    <h2>Profil</h2>
                    <p class="m-summary">{{ r.summary }}</p>
                  </section>
                }
              }
              @case ('experiences') {
                @if (r.experiences.length) {
                  <section>
                    <h2>Expérience</h2>
                    @for (e of r.experiences; track e.id) {
                      <article class="m-entry">
                        <div class="m-entry-head">
                          <strong>{{ e.jobTitle }}</strong>
                          <span class="m-dates">{{ e.startDate }} — {{ e.current ? 'Aujourd’hui' : e.endDate }}</span>
                        </div>
                        <div class="m-company">{{ companyLine(e) }}</div>
                        @if (e.description) {
                          <p>{{ e.description }}</p>
                        }
                        @if (e.achievements.length) {
                          <ul>
                            @for (a of e.achievements; track $index) {
                              <li>{{ a }}</li>
                            }
                          </ul>
                        }
                      </article>
                    }
                  </section>
                }
              }
              @case ('education') {
                @if (r.education.length) {
                  <section>
                    <h2>Formation</h2>
                    @for (ed of r.education; track ed.id) {
                      <article class="m-entry">
                        <div class="m-entry-head">
                          <strong>{{ ed.degree }}</strong>
                          <span class="m-dates">{{ ed.startDate }} — {{ ed.endDate }}</span>
                        </div>
                        <div class="m-company">{{ ed.school }}</div>
                        @if (ed.description) {
                          <p>{{ ed.description }}</p>
                        }
                      </article>
                    }
                  </section>
                }
              }
              @case ('projects') {
                @if (r.projects.length) {
                  <section>
                    <h2>Projets</h2>
                    @for (p of r.projects; track p.id) {
                      <article class="m-entry">
                        <strong>{{ p.name }}</strong>
                        @if (p.description) {
                          <p>{{ p.description }}</p>
                        }
                        @if (p.technologies.length) {
                          <div class="m-tags">
                            @for (t of p.technologies; track t) {
                              <span>{{ t }}</span>
                            }
                          </div>
                        }
                      </article>
                    }
                  </section>
                }
              }

              @case ('skills') {
                @if (r.skills.length) {
                  <section>
                    <h2>Compétences</h2>
                    @for (sk of r.skills; track sk.id) {
                      <div class="m-skill">
                        <span>{{ sk.name }}</span>
                        <div class="m-bar"><i [style.width.%]="sk.level * 20"></i></div>
                      </div>
                    }
                  </section>
                }
              }
              @case ('languages') {
                @if (r.languages.length) {
                  <section>
                    <h2>Langues</h2>
                    @for (l of r.languages; track l.id) {
                      <div class="m-lang">
                        <span>{{ l.name }}</span
                        ><em>{{ l.level }}</em>
                      </div>
                    }
                  </section>
                }
              }
              @case ('certifications') {
                @if (r.certifications.length) {
                  <section>
                    <h2>Certifications</h2>
                    @for (c of r.certifications; track c.id) {
                      <div class="m-cert">
                        <strong>{{ c.name }}</strong
                        ><span>{{ c.issuer }} · {{ c.date }}</span>
                      </div>
                    }
                  </section>
                }
              }
            }
          }
        </main>
      </div>
    </div>
  `,
  styles: `
    .m-cv {
      color: #1f2733;
    }
    .m-head {
      background: linear-gradient(135deg, var(--cv-primary), var(--cv-secondary));
      color: #fff;
      padding: 28px 32px;
      border-radius: 8px 8px 0 0;
    }
    .m-head h1 {
      margin: 0;
      font-size: 1.9em;
      font-weight: 700;
    }
    .m-title {
      margin: 4px 0 0;
      opacity: 0.9;
    }
    .m-contact {
      list-style: none;
      display: flex;
      flex-wrap: wrap;
      gap: 4px 16px;
      padding: 12px 0 0;
      margin: 0;
      font-size: 0.82em;
      opacity: 0.95;
    }
    .m-body {
      display: grid;
      grid-template-columns: 1fr;
      gap: 28px;
      padding: 26px 32px;
    }
    h2 {
      color: var(--cv-primary);
      font-size: 1.1em;
      text-transform: uppercase;
      letter-spacing: 0.04em;
      margin: 0 0 10px;
      border-bottom: 2px solid var(--cv-primary);
      padding-bottom: 4px;
    }
    h3 {
      color: var(--cv-primary);
      font-size: 0.95em;
      text-transform: uppercase;
      letter-spacing: 0.04em;
      margin: 0 0 8px;
    }
    section {
      margin-bottom: 18px;
    }
    .m-entry {
      margin-bottom: 12px;
    }
    .m-entry-head {
      display: flex;
      justify-content: space-between;
      gap: 8px;
    }
    .m-dates {
      color: #8a93a2;
      font-size: 0.82em;
      white-space: nowrap;
    }
    .m-company {
      color: var(--cv-secondary);
      font-weight: 600;
      font-size: 0.9em;
    }
    p {
      margin: 4px 0;
      line-height: 1.5;
    }
    ul {
      margin: 4px 0;
      padding-left: 18px;
    }
    .m-tags {
      display: flex;
      flex-wrap: wrap;
      gap: 4px;
      margin-top: 4px;
    }
    .m-tags span {
      background: rgba(102, 126, 234, 0.12);
      color: var(--cv-primary);
      border-radius: 10px;
      padding: 1px 8px;
      font-size: 0.78em;
    }
    .m-skill {
      margin-bottom: 8px;
      font-size: 0.85em;
    }
    .m-bar {
      background: #e7eaf0;
      border-radius: 4px;
      height: 6px;
      margin-top: 3px;
    }
    .m-bar i {
      display: block;
      height: 6px;
      border-radius: 4px;
      background: var(--cv-primary);
    }
    .m-lang,
    .m-cert {
      display: flex;
      justify-content: space-between;
      gap: 8px;
      font-size: 0.85em;
      margin-bottom: 6px;
    }
    .m-cert {
      flex-direction: column;
    }
    .m-cert span {
      color: #8a93a2;
      font-size: 0.82em;
    }
  `,
})
export class CvTemplateModernComponent {
  readonly resume = input.required<Resume>();
  /** Employer + role location on one line; empty when neither is filled in. */
  companyLine(e: Experience): string {
    return experienceCompanyLine(e);
  }

  readonly sections = computed<SectionKey[]>(() => {
    const r = this.resume();
    return r.sectionOrder.filter(s => !r.hiddenSections.includes(s));
  });
}
