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
