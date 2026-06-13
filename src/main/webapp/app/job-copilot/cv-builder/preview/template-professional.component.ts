import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Resume, SectionKey } from '../resume.model';

@Component({
  selector: 'jhi-cv-tmpl-professional',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let r = resume();
    <div class="p-cv">
      <header class="p-head">
        <h1>{{ r.personal.firstName }} {{ r.personal.lastName }}</h1>
        @if (r.personal.title) {
          <p class="p-title">{{ r.personal.title }}</p>
        }
        <p class="p-contact">{{ contactLine() }}</p>
      </header>

      @for (s of sections(); track s) {
        @switch (s) {
          @case ('summary') {
            @if (r.summary) {
              <section>
                <h2>Résumé professionnel</h2>
                <p>{{ r.summary }}</p>
              </section>
            }
          }
          @case ('experiences') {
            @if (r.experiences.length) {
              <section>
                <h2>Expérience professionnelle</h2>
                @for (e of r.experiences; track e.id) {
                  <article class="p-entry">
                    <div class="p-row">
                      <strong>{{ e.jobTitle }}, {{ e.company }}</strong
                      ><span>{{ e.startDate }} — {{ e.current ? 'Aujourd’hui' : e.endDate }}</span>
                    </div>
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
                  <article class="p-entry">
                    <div class="p-row">
                      <strong>{{ ed.degree }}, {{ ed.school }}</strong
                      ><span>{{ ed.startDate }} — {{ ed.endDate }}</span>
                    </div>
                    @if (ed.description) {
                      <p>{{ ed.description }}</p>
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
                <p>{{ skillNames() }}</p>
              </section>
            }
          }
          @case ('languages') {
            @if (r.languages.length) {
              <section>
                <h2>Langues</h2>
                <p>
                  @for (l of r.languages; track l.id) {
                    <span
                      >{{ l.name }} ({{ l.level }})
                      @if (!$last) {
                        ,
                      }
                    </span>
                  }
                </p>
              </section>
            }
          }
          @case ('projects') {
            @if (r.projects.length) {
              <section>
                <h2>Projets</h2>
                @for (p of r.projects; track p.id) {
                  <article class="p-entry">
                    <strong>{{ p.name }}</strong>
                    @if (p.description) {
                      <p>{{ p.description }}</p>
                    }
                  </article>
                }
              </section>
            }
          }
          @case ('certifications') {
            @if (r.certifications.length) {
              <section>
                <h2>Certifications</h2>
                @for (c of r.certifications; track c.id) {
                  <div class="p-row">
                    <strong>{{ c.name }}</strong
                    ><span>{{ c.issuer }} · {{ c.date }}</span>
                  </div>
                }
              </section>
            }
          }
        }
      }
    </div>
  `,
  styles: `
    .p-cv {
      color: #222;
      font-family: var(--cv-font);
    }
    .p-head {
      text-align: center;
      border-bottom: 3px double var(--cv-primary);
      padding-bottom: 14px;
      margin-bottom: 18px;
    }
    .p-head h1 {
      margin: 0;
      font-size: 2em;
      letter-spacing: 0.02em;
      color: var(--cv-primary);
    }
    .p-title {
      margin: 4px 0 0;
      font-style: italic;
      color: var(--cv-secondary);
    }
    .p-contact {
      margin: 10px 0 0;
      font-size: 0.82em;
      color: #555;
    }
    h2 {
      font-size: 1.05em;
      color: var(--cv-primary);
      border-bottom: 1px solid #d7d7d7;
      padding-bottom: 3px;
      margin: 0 0 8px;
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }
    section {
      margin-bottom: 16px;
    }
    .p-entry {
      margin-bottom: 10px;
    }
    .p-row {
      display: flex;
      justify-content: space-between;
      gap: 8px;
    }
    .p-row span {
      color: #777;
      font-size: 0.82em;
      white-space: nowrap;
    }
    p {
      margin: 4px 0;
      line-height: 1.5;
    }
    ul {
      margin: 4px 0;
      padding-left: 18px;
    }
  `,
})
export class CvTemplateProfessionalComponent {
  readonly resume = input.required<Resume>();
  readonly sections = computed<SectionKey[]>(() => {
    const r = this.resume();
    return r.sectionOrder.filter(s => !r.hiddenSections.includes(s));
  });
  readonly skillNames = computed(() =>
    this.resume()
      .skills.map(s => s.name)
      .filter(Boolean)
      .join(' · '),
  );
  readonly contactLine = computed(() => {
    const p = this.resume().personal;
    return [p.email, p.phone, p.location, p.linkedin, p.github, p.website].filter(Boolean).join('  ·  ');
  });
}
