import { diffResumes } from './resume-diff';
import { createEmptyResume } from './resume.model';

/** A resume with one project, the case that prompted this diff. */
function withProject() {
  const resume = createEmptyResume();
  return {
    ...resume,
    projects: [{ id: 'p1', name: 'Job Copilot', description: 'Recherche sémantique', technologies: '', url: '', bullets: [] }],
  } as ReturnType<typeof createEmptyResume>;
}

describe('resume diff', () => {
  it('reports nothing when the draft matches the server copy', () => {
    const resume = withProject();

    expect(diffResumes(resume, structuredClone(resume))).toEqual([]);
  });

  it('ignores updatedAt, which changes on every keystroke', () => {
    const server = withProject();
    const local = { ...server, updatedAt: new Date().toISOString() };

    expect(diffResumes(local, server)).toEqual([]);
  });

  it('ignores the server-assigned id', () => {
    const local = withProject();
    const server = { ...local, id: 'server-side-uuid' };

    expect(diffResumes(local, server)).toEqual([]);
  });

  it('reports a project the server does not have, with both counts', () => {
    const server = createEmptyResume();

    const diff = diffResumes(withProject(), server);

    expect(diff).toEqual([{ section: 'projects', localCount: 1, serverCount: 0 }]);
  });

  it('reports an edit inside an existing project', () => {
    const server = withProject();
    const local = { ...server, projects: [{ ...server.projects[0], description: 'Autre chose' }] };

    expect(diffResumes(local, server)).toEqual([{ section: 'projects', localCount: 1, serverCount: 1 }]);
  });

  it('treats a never-persisted resume as fully pending', () => {
    const diff = diffResumes(withProject(), null);

    expect(diff.map(d => d.section)).toContain('projects');
    expect(diff.map(d => d.section)).toContain('template');
  });

  it('reports scalar sections by name only', () => {
    const server = createEmptyResume();
    // 'Mon CV' is the default title, so it has to differ from it to count as a change.
    const local = { ...server, summary: 'Résumé', title: 'CV Développeur' };

    expect(diffResumes(local, server).map(d => d.section)).toEqual(['title', 'summary']);
  });

  it('folds section order and visibility into one layout entry', () => {
    const server = createEmptyResume();
    const local = { ...server, hiddenSections: ['projects' as const], sectionOrder: [...server.sectionOrder].reverse() };

    expect(diffResumes(local, server)).toEqual([{ section: 'layout' }]);
  });

  it('has nothing to say about a null draft', () => {
    expect(diffResumes(null, createEmptyResume())).toEqual([]);
  });
});
