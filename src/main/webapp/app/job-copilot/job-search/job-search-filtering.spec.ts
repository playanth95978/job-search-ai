import { applyFilters, buildFacet, hasActiveFilters, NO_FILTERS, offerCategory } from './job-search-filtering';
import { JobSearchFilters } from './job-search.types';

/** Fixed "now" so the date buckets are deterministic. */
const NOW = Date.parse('2026-08-19T12:00:00Z');
const daysAgo = (days: number): string => new Date(NOW - days * 24 * 60 * 60 * 1000).toISOString();

const OFFERS = [
  {
    title: 'Développeur Java',
    company: 'Acme',
    location: 'Nouméa',
    remote: false,
    contractType: 'CDI',
    salaryMin: 60000,
    salaryMax: 75000,
    createdAt: daysAgo(0.5),
  },
  {
    title: 'Data Analyst',
    company: 'Acme',
    location: 'Nouméa',
    remote: true,
    contractType: 'CDD',
    salaryMin: 45000,
    createdAt: daysAgo(3),
  },
  { title: 'Infirmier', company: 'Clinique', location: 'Koné', remote: false, contractType: 'CDI', createdAt: daysAgo(20) },
  { title: 'Chef de produit', company: 'Startup', location: 'Paris', remote: true, salaryMax: 90000, createdAt: daysAgo(60) },
];

const withFilters = (change: Partial<JobSearchFilters>): JobSearchFilters => ({ ...NO_FILTERS, ...change });

describe('job search filtering', () => {
  describe('offerCategory', () => {
    it('infers the job family from the title', () => {
      expect(offerCategory({ title: 'Développeur Java' })).toBe('tech');
      expect(offerCategory({ title: 'Data Analyst' })).toBe('data');
      expect(offerCategory({ title: 'Infirmier de nuit' })).toBe('health');
      expect(offerCategory({ title: 'Chef de produit' })).toBe('product');
    });

    it('falls back to "other" rather than guessing', () => {
      expect(offerCategory({ title: 'Poste polyvalent' })).toBe('other');
      expect(offerCategory({ title: '' })).toBe('other');
    });
  });

  describe('applyFilters', () => {
    it('keeps everything with the neutral state, in source order', () => {
      expect(applyFilters(OFFERS, NO_FILTERS, NOW)).toEqual(OFFERS);
    });

    it('keeps only remote offers', () => {
      const kept = applyFilters(OFFERS, withFilters({ remoteOnly: true }), NOW);

      expect(kept.map(o => o.title)).toEqual(['Data Analyst', 'Chef de produit']);
    });

    it('applies the date-posted bucket', () => {
      expect(applyFilters(OFFERS, withFilters({ datePosted: '24h' }), NOW).map(o => o.title)).toEqual(['Développeur Java']);
      expect(applyFilters(OFFERS, withFilters({ datePosted: '7d' }), NOW).map(o => o.title)).toEqual(['Développeur Java', 'Data Analyst']);
    });

    it('excludes offers with no date when a date bucket is set', () => {
      const undated = [{ title: 'Sans date' }];

      expect(applyFilters(undated, withFilters({ datePosted: '30d' }), NOW)).toEqual([]);
    });

    it('hides offers without an advertised salary when a minimum is set', () => {
      const kept = applyFilters(OFFERS, withFilters({ minSalary: 50000 }), NOW);

      // Infirmier advertises nothing, Data Analyst tops out at 45000.
      expect(kept.map(o => o.title)).toEqual(['Développeur Java', 'Chef de produit']);
    });

    it('treats multiple values of one facet as alternatives', () => {
      const kept = applyFilters(OFFERS, withFilters({ locations: ['Nouméa', 'Koné'] }), NOW);

      expect(kept.map(o => o.title)).toEqual(['Développeur Java', 'Data Analyst', 'Infirmier']);
    });

    it('combines different facets as a conjunction', () => {
      const kept = applyFilters(OFFERS, withFilters({ companies: ['Acme'], contractTypes: ['CDI'] }), NOW);

      expect(kept.map(o => o.title)).toEqual(['Développeur Java']);
    });

    it('sorts by date, most recent first', () => {
      const sorted = applyFilters(OFFERS, withFilters({ sort: 'date' }), NOW);

      expect(sorted.map(o => o.title)).toEqual(['Développeur Java', 'Data Analyst', 'Infirmier', 'Chef de produit']);
    });

    it('sorts by salary and sinks the offers that advertise none', () => {
      const sorted = applyFilters(OFFERS, withFilters({ sort: 'salary' }), NOW);

      expect(sorted.map(o => o.title)).toEqual(['Chef de produit', 'Développeur Java', 'Data Analyst', 'Infirmier']);
    });

    it('does not mutate the input array', () => {
      const input = [...OFFERS];

      applyFilters(input, withFilters({ sort: 'salary' }), NOW);

      expect(input).toEqual(OFFERS);
    });
  });

  describe('buildFacet', () => {
    it('counts values, most frequent first', () => {
      expect(buildFacet(OFFERS, o => o.company)).toEqual([
        { value: 'Acme', count: 2 },
        { value: 'Clinique', count: 1 },
        { value: 'Startup', count: 1 },
      ]);
    });

    it('skips offers that have no value for the field', () => {
      expect(buildFacet(OFFERS, o => o.contractType)).toEqual([
        { value: 'CDI', count: 2 },
        { value: 'CDD', count: 1 },
      ]);
    });

    it('caps the list', () => {
      expect(buildFacet(OFFERS, o => o.title, 2)).toHaveLength(2);
    });
  });

  describe('hasActiveFilters', () => {
    it('ignores the sort, which is a display preference and not a filter', () => {
      expect(hasActiveFilters(withFilters({ sort: 'salary' }))).toBe(false);
    });

    it('is true as soon as one criterion narrows the results', () => {
      expect(hasActiveFilters(withFilters({ remoteOnly: true }))).toBe(true);
      expect(hasActiveFilters(withFilters({ companies: ['Acme'] }))).toBe(true);
    });
  });
});
