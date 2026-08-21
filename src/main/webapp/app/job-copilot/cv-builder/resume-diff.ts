import { Resume } from './resume.model';

/** A part of the resume the diff reports on. Mirrors the editor's sections plus the document-level bits. */
export type ResumeDiffSection =
  | 'title'
  | 'template'
  | 'theme'
  | 'personal'
  | 'summary'
  | 'experiences'
  | 'education'
  | 'projects'
  | 'certifications'
  | 'skills'
  | 'languages'
  | 'referees'
  | 'layout';

/**
 * One difference between the working draft and what the server actually holds.
 *
 * @param localCount  items in the draft, for list sections.
 * @param serverCount items the server has, for list sections. A section the server has never seen
 *                    reports 0 — which is exactly the "my projects are not persisted" case.
 */
export interface ResumeDiffEntry {
  section: ResumeDiffSection;
  localCount?: number;
  serverCount?: number;
}

/** The list-valued sections, compared by content and reported with counts. */
const LIST_SECTIONS = ['experiences', 'education', 'projects', 'certifications', 'skills', 'languages', 'referees'] as const;

/**
 * Fields excluded from the comparison because they change without the document changing:
 * `updatedAt` is stamped on every keystroke, and `id` is assigned server-side.
 */
function comparable(resume: Resume): Omit<Resume, 'id' | 'updatedAt'> {
  const { id: _id, updatedAt: _updatedAt, ...rest } = resume;
  return rest;
}

/** Order-insensitive for nothing: the order of a resume's items is content, so plain JSON equality is right. */
function equal(a: unknown, b: unknown): boolean {
  return JSON.stringify(a ?? null) === JSON.stringify(b ?? null);
}

/**
 * What the draft holds that the server does not.
 *
 * <p>An empty result means the two agree — that is the only honest basis for telling the user their CV is
 * saved. A null `server` means nothing has ever been persisted, so every non-empty part is reported.
 */
export function diffResumes(local: Resume | null | undefined, server: Resume | null | undefined): ResumeDiffEntry[] {
  if (!local) {
    return [];
  }
  const entries: ResumeDiffEntry[] = [];
  const left = comparable(local);
  const right = server ? comparable(server) : null;

  if (left.title !== (right?.title ?? '')) {
    entries.push({ section: 'title' });
  }
  if (left.template !== right?.template) {
    entries.push({ section: 'template' });
  }
  if (!equal(left.theme, right?.theme)) {
    entries.push({ section: 'theme' });
  }
  if (!equal(left.personal, right?.personal)) {
    entries.push({ section: 'personal' });
  }
  if ((left.summary ?? '') !== (right?.summary ?? '')) {
    entries.push({ section: 'summary' });
  }

  for (const section of LIST_SECTIONS) {
    const localList = left[section] ?? [];
    const serverList = right?.[section] ?? [];
    if (!equal(localList, serverList)) {
      entries.push({ section, localCount: localList.length, serverCount: serverList.length });
    }
  }

  // Section order and visibility are one concern for the user ("mise en page"), not two.
  if (!equal(left.sectionOrder, right?.sectionOrder) || !equal(left.hiddenSections, right?.hiddenSections)) {
    entries.push({ section: 'layout' });
  }

  return entries;
}
