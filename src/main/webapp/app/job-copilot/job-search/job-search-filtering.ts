import { JobOffer } from '../shared/job-copilot.model';
import { DatePostedKey, JobSearchFilters, JobSortKey, OfferFacet } from './job-search.types';

/**
 * The only fields filtering needs. Kept structural rather than tied to {@link JobOffer} so the same
 * code filters plain offers and the {@code JobOfferDto} carried by match results, which differ on
 * fields the filters never read (`skills`, `description`).
 */
export type FilterableOffer = Pick<
  JobOffer,
  'title' | 'company' | 'location' | 'remote' | 'contractType' | 'salaryMin' | 'salaryMax' | 'createdAt'
>;

/** Neutral filter state: everything visible, source order preserved. */
export const NO_FILTERS: JobSearchFilters = {
  sort: 'relevance',
  datePosted: 'any',
  minSalary: null,
  remoteOnly: false,
  locations: [],
  categories: [],
  companies: [],
  contractTypes: [],
};

/**
 * Job families the Category facet offers. Sources have no shared taxonomy — Emploi.nc, Adzuna and
 * Greenhouse each categorise differently, and several send nothing at all — so the family is inferred
 * from the offer title, which is the one field every source fills. Keyword lists are matched in order,
 * first hit wins, and anything unmatched falls into `other`.
 */
const CATEGORY_KEYWORDS: { id: string; keywords: string[] }[] = [
  { id: 'data', keywords: ['data', 'donnée', 'donnees', 'analyst', 'analytics', 'bi ', 'machine learning', 'ia ', ' ai ', 'scientist'] },
  {
    id: 'tech',
    keywords: [
      'develop',
      'développ',
      'developp',
      'engineer',
      'ingénieur',
      'ingenieur',
      'software',
      'devops',
      'sysadmin',
      'système',
      'systeme',
      'réseau',
      'reseau',
      'cyber',
      'sécurité informatique',
      'fullstack',
      'full-stack',
      'backend',
      'frontend',
      'informatique',
      'technicien it',
    ],
  },
  { id: 'design', keywords: ['design', 'ux', 'ui ', 'graphis', 'infographi'] },
  { id: 'product', keywords: ['product owner', 'product manager', 'chef de produit', 'scrum', 'agile coach'] },
  { id: 'sales', keywords: ['commercial', 'sales', 'vente', 'vendeur', 'business developer', 'account manager'] },
  { id: 'marketing', keywords: ['marketing', 'communication', 'community manager', 'seo', 'growth'] },
  { id: 'finance', keywords: ['comptab', 'financ', 'audit', 'contrôleur de gestion', 'controleur de gestion', 'trésorerie'] },
  { id: 'hr', keywords: ['ressources humaines', 'recrut', 'talent', ' rh ', 'paie'] },
  { id: 'health', keywords: ['infirmi', 'médec', 'medec', 'soignant', 'aide-soignant', 'santé', 'pharmac', 'kiné'] },
  { id: 'education', keywords: ['enseign', 'formateur', 'professeur', 'éducateur', 'educateur'] },
  {
    id: 'construction',
    keywords: ['btp', 'chantier', 'maçon', 'macon', 'électricien', 'electricien', 'plombier', 'travaux', 'conducteur de travaux'],
  },
  { id: 'logistics', keywords: ['logistiq', 'magasinier', 'cariste', 'chauffeur', 'livreur', 'transport', 'supply chain'] },
  { id: 'hospitality', keywords: ['cuisin', 'serveur', 'restaurat', 'hôtel', 'hotel', 'barman', 'pâtissier'] },
  { id: 'admin', keywords: ['assistant', 'secrétaire', 'secretaire', 'administratif', 'accueil', 'gestionnaire'] },
];

/** The inferred job family of an offer. Always returns a value, `other` when nothing matches. */
export function offerCategory(offer: FilterableOffer): string {
  const title = ` ${(offer.title ?? '').toLowerCase()} `;
  for (const category of CATEGORY_KEYWORDS) {
    if (category.keywords.some(keyword => title.includes(keyword))) {
      return category.id;
    }
  }
  return 'other';
}

/** Cutoff for a "date posted" bucket, or null for "any time". */
function datePostedCutoff(key: DatePostedKey, now: number): number | null {
  const days: Record<Exclude<DatePostedKey, 'any'>, number> = { '24h': 1, '7d': 7, '30d': 30 };
  return key === 'any' ? null : now - days[key] * 24 * 60 * 60 * 1000;
}

/** The highest salary figure an offer advertises, or null when it advertises none. */
function offerSalary(offer: FilterableOffer): number | null {
  return offer.salaryMax ?? offer.salaryMin ?? null;
}

function matches(offer: FilterableOffer, filters: JobSearchFilters, now: number): boolean {
  if (filters.remoteOnly && !offer.remote) {
    return false;
  }

  const cutoff = datePostedCutoff(filters.datePosted, now);
  if (cutoff !== null) {
    // An offer with no date cannot be shown to satisfy a date filter: excluding it is the honest read.
    const posted = offer.createdAt ? Date.parse(offer.createdAt) : NaN;
    if (Number.isNaN(posted) || posted < cutoff) {
      return false;
    }
  }

  if (filters.minSalary !== null) {
    const salary = offerSalary(offer);
    if (salary === null || salary < filters.minSalary) {
      return false;
    }
  }

  if (filters.locations.length && !filters.locations.includes(offer.location ?? '')) {
    return false;
  }
  if (filters.companies.length && !filters.companies.includes(offer.company ?? '')) {
    return false;
  }
  if (filters.contractTypes.length && !filters.contractTypes.includes(offer.contractType ?? '')) {
    return false;
  }
  return !(filters.categories.length && !filters.categories.includes(offerCategory(offer)));
}

/**
 * Comparator for the chosen sort. `relevance` keeps the order the source (or the reranker) returned —
 * re-sorting it would throw away the ranking the user asked for.
 */
function comparator(sort: JobSortKey): ((a: FilterableOffer, b: FilterableOffer) => number) | null {
  switch (sort) {
    case 'date':
      return (a, b) => (b.createdAt ? Date.parse(b.createdAt) : 0) - (a.createdAt ? Date.parse(a.createdAt) : 0);
    case 'salary':
      // Offers without a salary sink to the bottom rather than sorting as zero among real figures.
      return (a, b) => (offerSalary(b) ?? -1) - (offerSalary(a) ?? -1);
    default:
      return null;
  }
}

/** Filters then sorts, without mutating the input array. */
export function applyFilters<T extends FilterableOffer>(offers: T[], filters: JobSearchFilters, now = Date.now()): T[] {
  const kept = offers.filter(offer => matches(offer, filters, now));
  const compare = comparator(filters.sort);
  return compare ? [...kept].sort(compare) : kept;
}

/**
 * Counts the distinct values of one offer field across the result set, most frequent first, so the
 * facet lists reflect what is actually on screen instead of a fixed vocabulary.
 */
export function buildFacet<T extends FilterableOffer>(
  offers: T[],
  pick: (offer: T) => string | undefined | null,
  limit = 12,
): OfferFacet[] {
  const counts = new Map<string, number>();
  for (const offer of offers) {
    const value = pick(offer)?.trim();
    if (value) {
      counts.set(value, (counts.get(value) ?? 0) + 1);
    }
  }
  return [...counts.entries()]
    .map(([value, count]) => ({ value, count }))
    .sort((a, b) => b.count - a.count || a.value.localeCompare(b.value))
    .slice(0, limit);
}

/** True when at least one criterion is narrowing the results (drives the "clear all" affordance). */
export function hasActiveFilters(filters: JobSearchFilters): boolean {
  return (
    filters.datePosted !== 'any' ||
    filters.minSalary !== null ||
    filters.remoteOnly ||
    filters.locations.length > 0 ||
    filters.categories.length > 0 ||
    filters.companies.length > 0 ||
    filters.contractTypes.length > 0
  );
}
