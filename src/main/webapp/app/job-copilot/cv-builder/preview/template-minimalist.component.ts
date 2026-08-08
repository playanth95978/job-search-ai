import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Resume, SectionKey } from '../resume.model';

@Component({
  selector: 'jhi-cv-tmpl-minimalist',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let r = resume();
    <div class="x-cv">
      <header class="x-head">
        <h1>{{ r.personal.firstName }} {{ r.personal.lastName }}</h1>
        @if (r.personal.title) {
          <span class="x-title">{{ r.personal.title }}</span>
        }
        <div class="x-contact">{{ contactLine() }}</div>
      </header>

      @for (s of sections(); track s) {
        @switch (s) {
          @case ('summary') {
            @if (r.summary) {
              <section>
                <p class="x-summary">{{ r.summary }}</p>
              </section>
            }
          }
          @case ('experiences') {
            @if (r.experiences.length) {
              <section>
                <h2>Expérience</h2>
                @for (e of r.experiences; track e.id) {
                  <article>
                    <div class="x-row">
                      <span class="x-strong">{{ e.jobTitle }} · {{ e.company }}</span
                      ><span class="x-date">{{ e.startDate }}–{{ e.current ? '' : e.endDate }}</span>
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
                  <div class="x-row">
                    <span class="x-strong">{{ ed.degree }} · {{ ed.school }}</span
                    ><span class="x-date">{{ ed.startDate }}–{{ ed.endDate }}</span>
                  </div>
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
                        ·
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
                  <div>
                    <span class="x-strong">{{ p.name }}</span>
                    @if (p.description) {
                      — {{ p.description }}
                    }
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
                  <div class="x-row">
                    <span>{{ c.name }}</span
                    ><span class="x-date">{{ c.issuer }} · {{ c.date }}</span>
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
    .x-cv {
      color: #2b2b2b;
      font-family: var(--cv-font);
      padding: 15px;
    }
    .x-head {
      margin-bottom: 22px;
    }
    .x-head h1 {
      margin: 0;
      font-size: 1.7em;
      font-weight: 600;
      letter-spacing: 0.01em;
    }
    .x-title {
      color: var(--cv-primary);
      font-size: 0.95em;
    }
    .x-contact {
      color: #888;
      font-size: 0.8em;
      margin-top: 6px;
    }
    h2 {
      font-size: 0.8em;
      text-transform: uppercase;
      letter-spacing: 0.12em;
      color: #999;
      margin: 0 0 8px;
      font-weight: 600;
    }
    section {
      margin-bottom: 18px;
    }
    article {
      margin-bottom: 10px;
    }
    .x-row {
      display: flex;
      justify-content: space-between;
      gap: 8px;
    }
    .x-strong {
      font-weight: 600;
    }
    .x-date {
      color: #aaa;
      font-size: 0.8em;
      white-space: nowrap;
    }
    p {
      margin: 3px 0;
      line-height: 1.55;
    }
    .x-summary {
      font-size: 1.02em;
    }
    ul {
      margin: 3px 0;
      padding-left: 16px;
    }
  `,
})
export class CvTemplateMinimalistComponent {
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
