import { computed, Injectable, signal } from '@angular/core';

import {
  createEmptyResume,
  Resume,
  ResumeTemplateId,
  ResumeTheme,
  SectionKey,
  emptyExperience,
  emptyEducation,
  emptyProject,
  emptyCertification,
  emptySkill,
  emptyLanguage,
  emptyReferee,
  Experience,
  Education,
  Project,
  Certification,
  Skill,
  Language,
  Referee,
} from './resume.model';

const DRAFT_KEY = 'jsa.cv-builder.draft';
const HISTORY_LIMIT = 50;
const HISTORY_DEBOUNCE_MS = 600;

/**
 * Signal-based store for the CV builder.
 *
 * - Single source of truth (`resume`) consumed by both the editor and the live preview.
 * - Immutable updates via {@link mutate}, with a debounced history so a burst of keystrokes
 *   collapses into one undo step.
 * - Auto-saves the working draft to localStorage.
 */
@Injectable({ providedIn: 'root' })
export class ResumeStore {
  private readonly _resume = signal<Resume>(this.loadDraft());
  readonly resume = this._resume.asReadonly();

  readonly canUndo = signal(false);
  readonly canRedo = signal(false);
  readonly savedAt = signal<Date | null>(null);

  // Convenience selectors used across the editor/preview.
  readonly template = computed(() => this._resume().template);
  readonly theme = computed(() => this._resume().theme);
  readonly visibleSections = computed<SectionKey[]>(() => {
    const r = this._resume();
    return r.sectionOrder.filter(s => !r.hiddenSections.includes(s));
  });

  private past: Resume[] = [];
  private future: Resume[] = [];
  private historyTimer: ReturnType<typeof setTimeout> | null = null;
  private pendingSnapshot: Resume | null = null;

  // ---- core update --------------------------------------------------------

  /** Applies an immutable change and schedules a (debounced) history checkpoint. */
  mutate(fn: (r: Resume) => Resume): void {
    const current = this._resume();
    if (this.pendingSnapshot === null) {
      this.pendingSnapshot = current; // first change of a burst
    }
    const next = { ...fn(current), updatedAt: new Date().toISOString() };
    this._resume.set(next);
    this.scheduleHistory();
    this.persist(next);
  }

  private scheduleHistory(): void {
    if (this.historyTimer) {
      clearTimeout(this.historyTimer);
    }
    this.historyTimer = setTimeout(() => this.commitHistory(), HISTORY_DEBOUNCE_MS);
  }

  private commitHistory(): void {
    if (this.pendingSnapshot && this.pendingSnapshot !== this._resume()) {
      this.past.push(this.pendingSnapshot);
      if (this.past.length > HISTORY_LIMIT) this.past.shift();
      this.future = [];
      this.syncFlags();
    }
    this.pendingSnapshot = null;
    this.historyTimer = null;
  }

  undo(): void {
    this.commitHistory();
    const prev = this.past.pop();
    if (!prev) return;
    this.future.push(this._resume());
    this._resume.set(prev);
    this.persist(prev);
    this.syncFlags();
  }

  redo(): void {
    const next = this.future.pop();
    if (!next) return;
    this.past.push(this._resume());
    this._resume.set(next);
    this.persist(next);
    this.syncFlags();
  }

  private syncFlags(): void {
    this.canUndo.set(this.past.length > 0);
    this.canRedo.set(this.future.length > 0);
  }

  // ---- meta ---------------------------------------------------------------

  setTitle(title: string): void {
    this.mutate(r => ({ ...r, title }));
  }

  setTemplate(template: ResumeTemplateId): void {
    this.mutate(r =>
      // Australian resumes lead with Key Skills, so bring that section up near the top on switch.
      template === 'australian' ? { ...r, template, sectionOrder: withSkillsAfterSummary(r.sectionOrder) } : { ...r, template },
    );
  }

  patchTheme(patch: Partial<ResumeTheme>): void {
    this.mutate(r => ({ ...r, theme: { ...r.theme, ...patch } }));
  }

  load(resume: Partial<Resume> | null | undefined): void {
    const safe = normalizeResume(resume);
    this.past = [];
    this.future = [];
    this.syncFlags();
    this._resume.set(safe);
    this.persist(safe);
  }

  reset(): void {
    this.load(createEmptyResume());
  }

  // ---- sections (reorder / visibility) ------------------------------------

  moveSection(section: SectionKey, dir: -1 | 1): void {
    this.mutate(r => {
      const order = [...r.sectionOrder];
      const i = order.indexOf(section);
      const j = i + dir;
      if (i < 0 || j < 0 || j >= order.length) return r;
      [order[i], order[j]] = [order[j], order[i]];
      return { ...r, sectionOrder: order };
    });
  }

  toggleSection(section: SectionKey): void {
    this.mutate(r => {
      const hidden = r.hiddenSections.includes(section) ? r.hiddenSections.filter(s => s !== section) : [...r.hiddenSections, section];
      return { ...r, hiddenSections: hidden };
    });
  }

  // ---- personal / summary -------------------------------------------------

  patchPersonal(patch: Partial<Resume['personal']>): void {
    this.mutate(r => ({ ...r, personal: { ...r.personal, ...patch } }));
  }

  setSummary(summary: string): void {
    this.mutate(r => ({ ...r, summary }));
  }

  // ---- generic list helpers ----------------------------------------------

  private addItem<K extends keyof Resume>(key: K, item: Resume[K] extends (infer T)[] ? T : never): void {
    this.mutate(r => ({ ...r, [key]: [...(r[key] as unknown[]), item] }));
  }

  private removeItem(key: keyof Resume, id: string): void {
    this.mutate(r => ({ ...r, [key]: (r[key] as { id: string }[]).filter(it => it.id !== id) }));
  }

  private patchItem(key: keyof Resume, id: string, patch: Record<string, unknown>): void {
    this.mutate(r => ({
      ...r,
      [key]: (r[key] as { id: string }[]).map(it => (it.id === id ? { ...it, ...patch } : it)),
    }));
  }

  private moveItem(key: keyof Resume, id: string, dir: -1 | 1): void {
    this.mutate(r => {
      const list = [...(r[key] as { id: string }[])];
      const i = list.findIndex(it => it.id === id);
      const j = i + dir;
      if (i < 0 || j < 0 || j >= list.length) return r;
      [list[i], list[j]] = [list[j], list[i]];
      return { ...r, [key]: list };
    });
  }

  // experiences
  addExperience(): void {
    this.addItem('experiences', emptyExperience());
  }
  removeExperience(id: string): void {
    this.removeItem('experiences', id);
  }
  patchExperience(id: string, patch: Partial<Experience>): void {
    this.patchItem('experiences', id, patch);
  }
  moveExperience(id: string, dir: -1 | 1): void {
    this.moveItem('experiences', id, dir);
  }

  // education
  addEducation(): void {
    this.addItem('education', emptyEducation());
  }
  removeEducation(id: string): void {
    this.removeItem('education', id);
  }
  patchEducation(id: string, patch: Partial<Education>): void {
    this.patchItem('education', id, patch);
  }
  moveEducation(id: string, dir: -1 | 1): void {
    this.moveItem('education', id, dir);
  }

  // projects
  addProject(): void {
    this.addItem('projects', emptyProject());
  }
  removeProject(id: string): void {
    this.removeItem('projects', id);
  }
  patchProject(id: string, patch: Partial<Project>): void {
    this.patchItem('projects', id, patch);
  }
  moveProject(id: string, dir: -1 | 1): void {
    this.moveItem('projects', id, dir);
  }

  // certifications
  addCertification(): void {
    this.addItem('certifications', emptyCertification());
  }
  removeCertification(id: string): void {
    this.removeItem('certifications', id);
  }
  patchCertification(id: string, patch: Partial<Certification>): void {
    this.patchItem('certifications', id, patch);
  }

  // skills
  addSkill(): void {
    this.addItem('skills', emptySkill());
  }
  removeSkill(id: string): void {
    this.removeItem('skills', id);
  }
  patchSkill(id: string, patch: Partial<Skill>): void {
    this.patchItem('skills', id, patch);
  }

  // languages
  addLanguage(): void {
    this.addItem('languages', emptyLanguage());
  }
  removeLanguage(id: string): void {
    this.removeItem('languages', id);
  }
  patchLanguage(id: string, patch: Partial<Language>): void {
    this.patchItem('languages', id, patch);
  }

  // referees
  addReferee(): void {
    this.addItem('referees', emptyReferee());
  }
  removeReferee(id: string): void {
    this.removeItem('referees', id);
  }
  patchReferee(id: string, patch: Partial<Referee>): void {
    this.patchItem('referees', id, patch);
  }

  // ---- persistence --------------------------------------------------------

  private persist(resume: Resume): void {
    try {
      localStorage.setItem(DRAFT_KEY, JSON.stringify(resume));
      this.savedAt.set(new Date());
    } catch {
      // localStorage may be unavailable (private mode / quota) — ignore.
    }
  }

  private loadDraft(): Resume {
    try {
      const raw = localStorage.getItem(DRAFT_KEY);
      if (raw) {
        return normalizeResume(JSON.parse(raw) as Partial<Resume>);
      }
    } catch {
      // ignore corrupt draft
    }
    return createEmptyResume();
  }
}

/** Moves "skills" to right after "summary" (or to the front if no summary), preserving every other section's order. */
function withSkillsAfterSummary(order: SectionKey[]): SectionKey[] {
  const next: SectionKey[] = order.filter(s => s !== 'skills');
  const summaryIdx = next.indexOf('summary');
  next.splice(summaryIdx >= 0 ? summaryIdx + 1 : 0, 0, 'skills');
  return next;
}

/**
 * Guarantees every nested structure exists (theme, personal, lists), so a resume loaded from the
 * server / an old draft / an import can never crash the preview with `undefined` reads.
 */
function normalizeResume(input: Partial<Resume> | null | undefined): Resume {
  const base = createEmptyResume();
  if (!input) {
    return base;
  }
  return {
    ...base,
    ...input,
    personal: { ...base.personal, ...(input.personal ?? {}) },
    theme: { ...base.theme, ...(input.theme ?? {}) },
    experiences: input.experiences ?? [],
    education: input.education ?? [],
    projects: input.projects ?? [],
    certifications: input.certifications ?? [],
    skills: input.skills ?? [],
    languages: input.languages ?? [],
    referees: input.referees ?? [],
    sectionOrder: input.sectionOrder?.length ? input.sectionOrder : base.sectionOrder,
    hiddenSections: input.hiddenSections ?? [],
    template: input.template ?? base.template,
    title: input.title ?? base.title,
  };
}
