import { JobOfferPreview } from '../shared/job-copilot.model';

/** Shared shape of an offer shown in the detail pane — satisfied by both JobOffer and JobOfferDto. */
export type OfferDetail = JobOfferPreview & {
  id?: string;
  remote?: boolean;
  contractType?: string;
  salaryMin?: number;
  salaryMax?: number;
};

/** Criteria emitted by the search form for the parent to run a search/match. */
export interface JobSearchCriteria {
  keywords?: string;
  location?: string;
  source?: string;
  country?: string;
}

/** Sort order of the results column. `relevance` keeps the order the source/reranker returned. */
export type JobSortKey = 'relevance' | 'date' | 'salary';

/** "Date posted" buckets offered by the filter panel. */
export type DatePostedKey = 'any' | '24h' | '7d' | '30d';

/** One value of a facet list, with how many of the current results carry it. */
export interface OfferFacet {
  value: string;
  count: number;
}

/** State of the left-hand filter panel. Applied client-side to the results already loaded. */
export interface JobSearchFilters {
  sort: JobSortKey;
  datePosted: DatePostedKey;
  /** Lower bound on the advertised salary; null means "no minimum". */
  minSalary: number | null;
  remoteOnly: boolean;
  locations: string[];
  categories: string[];
  companies: string[];
  contractTypes: string[];
}
