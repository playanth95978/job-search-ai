import { describe, expect, it } from 'vitest';

import { CandidateProfile } from './job-copilot.model';
import { analyzeSearchability, AtsCheck, AtsCheckStatus, parseRateRating } from './ats-searchability';

function check(checks: AtsCheck[], id: string): AtsCheck {
  const found = checks.find(c => c.id === id);
  expect(found, `check "${id}" should exist`).toBeDefined();
  return found!;
}

function status(checks: AtsCheck[], id: string): AtsCheckStatus {
  return check(checks, id).status;
}

/** A well-structured CV with standard section headings in the raw text. */
const goodProfile: CandidateProfile = {
  fullName: 'Anthony Attia',
  email: 'anthony@gmail.com',
  location: 'Nouméa',
  yearsOfExperience: 6,
  skills: [
    { id: 'java', name: 'Java', level: 5 },
    { id: 'spring', name: 'Spring Boot', level: 5 },
    { id: 'angular', name: 'Angular', level: 4 },
    { id: 'postgres', name: 'PostgreSQL', level: 4 },
    { id: 'docker', name: 'Docker', level: 3 },
  ],
  experiences: [
    { id: 'opt', jobTitle: 'Développeur Full Stack', company: 'OPT', startDate: '2024', current: true },
    { id: 'prev', jobTitle: 'Développeur Java', company: 'ACME', startDate: '2019', endDate: '2023' },
  ],
  education: [{ id: 'lic', degree: 'Licence Informatique', school: 'UNC', startDate: '2016', endDate: '2019' }],
  rawMarkdown: [
    '# Anthony Attia',
    'anthony@gmail.com · +687 12 34 56 · https://linkedin.com/in/anthony',
    '',
    '## Expérience professionnelle',
    'Développeur Full Stack — OPT (2024 - Aujourd’hui)',
    'Développeur Java — ACME (2019 - 2023)',
    '',
    '## Formation',
    'Licence Informatique — UNC (2016 - 2019)',
    '',
    '## Compétences',
    'Java, Spring Boot, Angular, PostgreSQL, Docker',
  ].join('\n'),
};

describe('analyzeSearchability', () => {
  describe('well-structured CV', () => {
    const result = analyzeSearchability(goodProfile);

    it('extracts the contact block', () => {
      expect(result.contact.name).toBe('Anthony Attia');
      expect(result.contact.email).toBe('anthony@gmail.com');
      expect(result.contact.phone).toContain('687');
      expect(result.contact.linkedin).toContain('linkedin.com/in/anthony');
    });

    it('passes all contact checks', () => {
      expect(status(result.checks, 'name')).toBe('pass');
      expect(status(result.checks, 'email')).toBe('pass');
      expect(status(result.checks, 'phone')).toBe('pass');
      expect(status(result.checks, 'links')).toBe('pass');
    });

    it('passes section checks when heading + data are present', () => {
      expect(status(result.checks, 'experienceSection')).toBe('pass');
      expect(status(result.checks, 'educationSection')).toBe('pass');
      expect(status(result.checks, 'skillsSection')).toBe('pass');
    });

    it('passes date and skill quality checks', () => {
      expect(status(result.checks, 'dates')).toBe('pass');
      expect(status(result.checks, 'skillsCount')).toBe('pass');
    });

    it('yields a high, good-rated parse rate', () => {
      expect(result.parseRate).toBeGreaterThanOrEqual(80);
      expect(parseRateRating(result.parseRate)).toBe('good');
      expect(result.passed).toBe(result.total);
    });
  });

  describe('empty profile', () => {
    const result = analyzeSearchability({});

    it('fails the critical contact and section checks', () => {
      expect(status(result.checks, 'name')).toBe('fail');
      expect(status(result.checks, 'email')).toBe('fail');
      expect(status(result.checks, 'experienceSection')).toBe('fail');
      expect(status(result.checks, 'skillsCount')).toBe('fail');
    });

    it('omits the dates check when there are no experiences', () => {
      expect(result.checks.find(c => c.id === 'dates')).toBeUndefined();
    });

    it('yields a poor parse rate', () => {
      // Only optional contact fields (phone/links/location) score partial "warn" credit,
      // so an empty profile stays well within the poor band.
      expect(result.parseRate).toBeLessThan(20);
      expect(parseRateRating(result.parseRate)).toBe('poor');
    });
  });

  describe('data present but non-standard section headings', () => {
    const result = analyzeSearchability({
      fullName: 'Jane Doe',
      skills: [{ id: 's', name: 'Go' }],
      experiences: [{ id: 'x', jobTitle: 'Dev', company: 'Corp', startDate: '2020', endDate: '2022' }],
      // "Mon parcours" / "Ce que je maîtrise" are not recognized headings.
      rawMarkdown: 'Jane Doe\n\n## Mon parcours\nDev — Corp (2020-2022)\n\n## Ce que je maîtrise\nGo',
    });

    it('warns rather than passes when a heading is non-standard but data was extracted', () => {
      expect(status(result.checks, 'experienceSection')).toBe('warn');
      expect(status(result.checks, 'skillsSection')).toBe('warn');
    });
  });

  describe('email recovered from raw text when structured field is empty', () => {
    const result = analyzeSearchability({
      fullName: 'No Email Field',
      email: '',
      rawMarkdown: 'Contact: recovered@example.com',
    });

    it('falls back to a regex match on the raw text', () => {
      expect(result.contact.email).toBe('recovered@example.com');
      expect(status(result.checks, 'email')).toBe('pass');
    });
  });

  describe('experiences without readable dates', () => {
    const result = analyzeSearchability({
      fullName: 'Undated Person',
      experiences: [
        { id: 'a', jobTitle: 'Dev', company: 'A', startDate: '2021', endDate: '2023' },
        { id: 'b', jobTitle: 'Intern', company: 'B' }, // no dates, not current
      ],
      rawMarkdown: '## Expérience\nDev\nIntern',
    });

    it('warns when only some experiences carry parseable dates', () => {
      expect(status(result.checks, 'dates')).toBe('warn');
      expect(check(result.checks, 'dates').detailParams).toMatchObject({ dated: 1, total: 2 });
    });
  });

  describe('parseRateRating thresholds', () => {
    it('maps rate bands to ratings', () => {
      expect(parseRateRating(95)).toBe('good');
      expect(parseRateRating(80)).toBe('good');
      expect(parseRateRating(70)).toBe('fair');
      expect(parseRateRating(55)).toBe('fair');
      expect(parseRateRating(40)).toBe('poor');
    });
  });
});
