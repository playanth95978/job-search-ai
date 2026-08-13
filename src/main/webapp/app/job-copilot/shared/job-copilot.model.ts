import {
  CandidateProfileCertificationDto,
  CandidateProfileEducationDto,
  CandidateProfileExperienceDto,
  CandidateProfileLanguageDto,
  CandidateProfileSkillDto,
} from '../cv-builder/candidate-profile.mapper';

export interface CandidateProfile {
  id?: string;
  userId?: string;
  fullName?: string;
  email?: string;
  location?: string;
  yearsOfExperience?: number;
  skills?: CandidateProfileSkillDto[];
  experiences?: CandidateProfileExperienceDto[];
  preferredRoles?: string[];
  languages?: CandidateProfileLanguageDto[];
  education?: CandidateProfileEducationDto[];
  certifications?: CandidateProfileCertificationDto[];
  rawMarkdown?: string;
  cvFilename?: string;
}

export interface JobOffer {
  id?: string;
  title: string;
  company?: string;
  location?: string;
  remote?: boolean;
  description?: string;
  skills?: string;
  source?: string;
  sourceId?: string;
  applyUrl?: string;
  salaryMin?: number;
  salaryMax?: number;
  contractType?: string;
  createdAt?: string;
}

export interface JobOfferDto {
  id?: string;
  title: string;
  company?: string;
  location?: string;
  remote: boolean;
  description?: string;
  skills: string[];
  source?: string;
  applyUrl?: string;
  salaryMin?: number;
  salaryMax?: number;
  contractType?: string;
}

/** A translatable match tag: `key` is an i18n key (jobCopilot.match.tag.*), `skill` an optional interpolation arg. */
export interface MatchTag {
  key: string;
  skill?: string;
}

export interface MatchResult {
  jobOffer: JobOfferDto;
  score: number;
  strengths: MatchTag[];
  weaknesses: MatchTag[];
}

export interface JobApplication {
  id?: string;
  userId: string;
  jobOffer?: JobOffer;
  candidateProfile?: CandidateProfile;
  status: ApplicationStatus;
  coverLetter?: string;
  notes?: string;
  matchScore?: number;
  createdAt?: string;
  updatedAt?: string;
  appliedAt?: string;
}

export type ApplicationStatus = 'DRAFT' | 'APPLIED' | 'INTERVIEW' | 'REJECTED' | 'GHOSTED' | 'OFFER' | 'WITHDRAWN';

export interface UserPreference {
  id?: string;
  userId?: string;
  remoteOnly?: boolean;
  contractType?: string;
  salaryMin?: number;
  salaryMax?: number;
  preferredRoles?: string;
  excludedTechnologies?: string;
  preferredLocations?: string;
}

export interface UserPreferenceDto {
  remoteOnly?: boolean;
  contractType?: string;
  salaryMin?: number;
  salaryMax?: number;
  preferredRoles: string[];
  excludedTechnologies: string[];
  preferredLocations: string[];
}

export type ApplicationStats = Record<string, number>;

export interface CvReview {
  score: number;
  summary: string;
  strengths: string[];
  improvements: string[];
}

/** AI positioning / match result of a CV against a job offer (all scores 0..100). */
export interface CvMatchResult {
  globalScore: number;
  skillsScore: number;
  experienceScore: number;
  keywordScore: number;
  missingSkills: string[];
  missingKeywords: string[];
  strengths: string[];
  weaknesses: string[];
  suggestions: string[];
}

export interface InterviewMessage {
  conversationId: string;
  message: string;
}

/** One turn of the Shadow Interview Panel: which juror spoke and what they said. */
export interface PanelMessage {
  conversationId: string;
  role: string;
  speaker: string;
  message: string;
}

/** One juror's final verdict in the panel cross deliberation. */
export interface PanelJurorReport {
  role: string;
  speaker: string;
  score: number;
  strengths: string[];
  concerns: string[];
  recommendation: string;
}

/** The panel's cross deliberation delivered at the end of the interview. */
export interface PanelReport {
  conversationId: string;
  jurors: PanelJurorReport[];
  overallScore: number;
  recommendation: string;
  synthesis: string;
}

/** A persisted chat-memory message (Spring AI), as returned by /api/chat-history/history/{id}. */
export interface ChatMessageHistory {
  conversationId: string;
  content: string;
  type: 'USER' | 'ASSISTANT' | 'SYSTEM' | string;
  timestamp: string;
}

/** One assistant reply, as returned by the tool-enabled agent endpoint. */
export interface AssistantMessage {
  conversationId: string;
  message: string;
}

/** Minimal offer shape needed to display the apply modal — satisfied by both JobOffer and JobOfferDto. */
export interface JobOfferPreview {
  title: string;
  company?: string;
  location?: string;
  source?: string;
  applyUrl?: string;
  /** Full description, populated lazily once the offer is enriched on open. */
  description?: string;
}

export const JOB_SOURCE_LABELS: Record<string, string> = {
  GREENHOUSE: 'Greenhouse',
  LEVER: 'Lever',
  REMOTEOK: 'RemoteOK',
  ADZUNA: 'Adzuna',
  HELLOWORK: 'HelloWork',
  SEEK: 'Seek AU',
  FRANCE_TRAVAIL: 'France Travail',
  EMPLOI_NC: 'Emploi.nc',
  MANUAL: 'Manual',
  JOBICY: 'jobicy',
  CAREERJET: 'Career Jet',
};

export const JOB_SOURCE_COLORS: Record<string, string> = {
  GREENHOUSE: '#43a047',
  LEVER: '#1565c0',
  REMOTEOK: '#ff6f00',
  ADZUNA: '#7e57c2',
  HELLOWORK: '#e91e63',
  SEEK: '#0d47a1',
  FRANCE_TRAVAIL: '#1a237e',
  EMPLOI_NC: '#00897b',
};

/**
 * Metadata to guide the user on what a source actually covers, so they don't search e.g. an
 * Australian city on a France-only source. `coverageKey` is an i18n key; `examples` are sample
 * valid locations; `hasCountrySelect` marks sources whose market is chosen per search (Adzuna).
 */
export interface JobSourceInfo {
  value: string;
  label: string;
  coverageKey: string;
  examples: string[];
  hasCountrySelect?: boolean;
}

/** Connectors that support keyword search (matches the registered backend AtsConnector beans). */
export const SEARCHABLE_JOB_SOURCES: JobSourceInfo[] = [
  {
    value: 'EMPLOI_NC',
    label: JOB_SOURCE_LABELS['EMPLOI_NC'],
    coverageKey: 'jobCopilot.search.coverage.emploiNc',
    examples: ['Nouméa', 'Dumbéa', 'Mont-Dore'],
  },
  {
    value: 'FRANCE_TRAVAIL',
    label: JOB_SOURCE_LABELS['FRANCE_TRAVAIL'],
    coverageKey: 'jobCopilot.search.coverage.franceTravail',
    examples: ['Paris', 'Lyon', 'Marseille', '75', '69'],
  },
  {
    value: 'ADZUNA',
    label: JOB_SOURCE_LABELS['ADZUNA'],
    coverageKey: 'jobCopilot.search.coverage.adzuna',
    examples: ['Paris', 'London', 'Brisbane', 'Berlin'],
    hasCountrySelect: true,
  },
  {
    value: 'JOBICY',
    label: JOB_SOURCE_LABELS['JOBICY'],
    coverageKey: 'jobCopilot.search.coverage.jobicy',
    examples: ['Paris', 'London', 'Brisbane', 'Berlin'],
    hasCountrySelect: true,
  },
  {
    value: 'CAREERJET',
    label: JOB_SOURCE_LABELS['CAREERJET'],
    coverageKey: 'jobCopilot.search.coverage.careerjet',
    examples: ['Paris', 'London', 'Brisbane', 'Berlin'],
    hasCountrySelect: true,
  },
  // { value: 'REMOTEOK', ... }  // international remote tech
  // { value: 'HELLOWORK', ... }
  // Seek is disabled by default (bot-protected); re-add when application.job-copilot.seek.enabled=true.
];

/** Adzuna markets exposed in the UI (the country is a path segment in the Adzuna API). */
export const ADZUNA_COUNTRIES: { code: string; label: string }[] = [
  { code: 'fr', label: 'France' },
  { code: 'gb', label: 'United Kingdom' },
  { code: 'us', label: 'United States' },
  { code: 'au', label: 'Australia' },
  { code: 'de', label: 'Germany' },
  { code: 'ca', label: 'Canada' },
];

export function jobSourceLabel(source?: string): string {
  return JOB_SOURCE_LABELS[source || ''] || source || '';
}

export function jobSourceColor(source?: string): string {
  return JOB_SOURCE_COLORS[source || ''] || '#4f46e5';
}

export const APPLICATION_STATUS_ICONS: Record<string, string> = {
  DRAFT: 'file-alt',
  APPLIED: 'paper-plane',
  INTERVIEW: 'user-tie',
  REJECTED: 'circle-xmark',
  GHOSTED: 'ghost',
  OFFER: 'handshake',
  WITHDRAWN: 'right-from-bracket',
};

export const APPLICATION_STATUS_COLORS: Record<string, string> = {
  DRAFT: '#94a3b8',
  APPLIED: '#3b82f6',
  INTERVIEW: '#8b5cf6',
  REJECTED: '#ef4444',
  GHOSTED: '#f59e0b',
  OFFER: '#10b981',
  WITHDRAWN: '#6b7280',
};

export function applicationStatusIcon(status: string): string {
  return APPLICATION_STATUS_ICONS[status] || 'circle-info';
}

export function applicationStatusColor(status: string): string {
  return APPLICATION_STATUS_COLORS[status] || '#64748b';
}

// --- Opportunity Radar (proactive new-offer matching) ---

export interface RadarHit {
  id: string;
  jobOffer: JobOfferDto;
  score: number;
  whyYou: MatchTag[];
  seen: boolean;
  dismissed: boolean;
  createdAt: string;
}

// --- Application Autopsy (rejection feedback loop) ---

export interface AutopsyReport {
  sampleSize: number;
  enoughData: boolean;
  funnel: {
    applied: number;
    interview: number;
    offer: number;
    rejected: number;
    ghosted: number;
    interviewRate: number;
    offerRate: number;
    ghostRate: number;
  };
  score: { avgPositive: number | null; avgNegative: number | null; suggestedThreshold: number | null } | null;
  salary: { medianPositive: number | null; medianNegative: number | null; signal: boolean } | null;
  skillDrivers: { skill: string; positiveCount: number; negativeCount: number; lift: number }[];
  recommendations: string[];
}

export interface AtsWarning {
  type: string;
  level: string;
  message: string;
  snippet: string | null;
}

export interface AtsSimulation {
  rawText: string;
  warnings: AtsWarning[];
  recommendations: string[];
}

// --- Predictive career-path simulator ---

export interface SkillGap {
  skill: string;
  demandCount: number;
  demandPct: number;
  owned: boolean;
}

export type RoadmapStepType = 'ROLE' | 'CERTIFICATION' | 'PROJECT' | 'SKILL';

export interface RoadmapStep {
  order: number;
  type: RoadmapStepType;
  title: string;
  rationale: string;
  relatedSkills: string[];
  evidenceOfferIds: string[];
}

export interface CareerRoadmap {
  targetRole: string;
  horizonYears: number;
  readinessScore: number;
  offersAnalyzed: number;
  skillGaps: SkillGap[];
  intermediateRoles: string[];
  steps: RoadmapStep[];
  sampleOffers: JobOfferDto[];
}

/** Board ATS suggéré pour le profil du candidat — déjà vérifié côté serveur (slug et URL réels). */
export interface BoardSuggestion {
  company: string;
  source: string;
  slug: string;
  boardUrl: string;
  offerCount: number;
  sampleTitles: string[];
}

/**
 * Langues proposées pour la traduction d'une offre. `value` part telle quelle dans le prompt du
 * modèle (« Translate the following job offer into French »), `label` est ce que voit l'utilisateur.
 */
export const TRANSLATION_LANGUAGES: { value: string; label: string }[] = [
  { value: 'French', label: 'Français' },
  { value: 'English', label: 'English' },
  { value: 'Spanish', label: 'Español' },
  { value: 'German', label: 'Deutsch' },
  { value: 'Italian', label: 'Italiano' },
  { value: 'Portuguese', label: 'Português' },
];
