import { ResumeStore } from './resume-store';
import { ALL_SECTIONS, SectionKey } from './resume.model';

/**
 * The section order is what the templates walk to render, so anything that mangles it either reorders
 * the CV behind the user's back or makes a section vanish. These cases are the ones observed in the wild.
 */
describe('ResumeStore section order', () => {
  let store: ResumeStore;

  beforeEach(() => {
    localStorage.clear();
    store = new ResumeStore();
  });

  it('keeps the user order on load', () => {
    const order: SectionKey[] = ['summary', 'skills', 'experiences', 'education', 'languages', 'projects', 'certifications'];

    store.load({ sectionOrder: order });

    expect(store.resume().sectionOrder).toEqual(order);
  });

  it('restores a section missing from a stored order, at its default position', () => {
    // A resume saved before "projects" existed: without repair the section would never render at all.
    store.load({ sectionOrder: ['summary', 'skills', 'experiences', 'education', 'languages', 'certifications'] });

    const order = store.resume().sectionOrder;

    expect(order).toContain('projects');
    expect(order.indexOf('projects')).toBeLessThan(order.indexOf('certifications'));
    expect(order).toHaveLength(ALL_SECTIONS.length);
  });

  it('does not append a restored section at the very end', () => {
    store.load({ sectionOrder: ['summary', 'experiences'] });

    const order = store.resume().sectionOrder;

    // "skills" drifting to the bottom of the document is the exact symptom this repair addresses.
    expect(order.at(-1)).not.toBe('skills');
    expect(order.indexOf('skills')).toBeLessThan(order.indexOf('languages'));
  });

  it('drops duplicates and unknown keys', () => {
    store.load({ sectionOrder: ['summary', 'summary', 'nope', 'skills'] as SectionKey[] });

    const order = store.resume().sectionOrder;

    expect(order.filter(s => s === 'summary')).toHaveLength(1);
    expect(order as string[]).not.toContain('nope');
    expect(order).toHaveLength(ALL_SECTIONS.length);
  });

  it('falls back to the default order when there is none', () => {
    store.load({ sectionOrder: [] });

    expect(store.resume().sectionOrder).toEqual([...ALL_SECTIONS]);
  });

  it('moves skills up when switching to the Australian template, without losing a section', () => {
    store.load({ sectionOrder: [...ALL_SECTIONS] });

    store.setTemplate('australian');

    const order = store.resume().sectionOrder;
    expect(order.indexOf('skills')).toBe(order.indexOf('summary') + 1);
    expect(order).toHaveLength(ALL_SECTIONS.length);
  });

  it('leaves the order alone when hiding a section', () => {
    store.load({ sectionOrder: [...ALL_SECTIONS] });
    const before = store.resume().sectionOrder;

    store.toggleSection('skills');

    expect(store.resume().sectionOrder).toEqual(before);
    expect(store.resume().hiddenSections).toEqual(['skills']);
  });
});
