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

export interface ApplicationStats {
  [key: string]: number;
}

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

export interface ApplyOutcome {
  autoApplied: boolean;
  autoApplySupported: boolean;
  message: string;
  application: JobApplication;
}

export interface InterviewMessage {
  conversationId: string;
  message: string;
}

/** A persisted chat-memory message (Spring AI), as returned by /api/chat-history/history/{id}. */
export interface ChatMessageHistory {
  conversationId: string;
  content: string;
  type: 'USER' | 'ASSISTANT' | 'SYSTEM' | string;
  timestamp: string;
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
  HELLOWORK: 'HelloWork',
  SEEK: 'Seek AU',
  FRANCE_TRAVAIL: 'France Travail',
  EMPLOI_NC: 'Emploi.nc',
  MANUAL: 'Manual',
};

export const JOB_SOURCE_COLORS: Record<string, string> = {
  GREENHOUSE: '#43a047',
  LEVER: '#1565c0',
  REMOTEOK: '#ff6f00',
  HELLOWORK: '#e91e63',
  SEEK: '#0d47a1',
  FRANCE_TRAVAIL: '#1a237e',
  EMPLOI_NC: '#00897b',
};

/** Connectors that support keyword search (matches the registered backend AtsConnector beans). */
export const SEARCHABLE_JOB_SOURCES: { value: string; label: string }[] = [
  { value: 'EMPLOI_NC', label: JOB_SOURCE_LABELS['EMPLOI_NC'] },
  { value: 'FRANCE_TRAVAIL', label: JOB_SOURCE_LABELS['FRANCE_TRAVAIL'] },
  // { value: 'REMOTEOK', label: JOB_SOURCE_LABELS['REMOTEOK'] },
  // { value: 'HELLOWORK', label: JOB_SOURCE_LABELS['HELLOWORK'] },
  // { value: 'GREENHOUSE', label: JOB_SOURCE_LABELS['GREENHOUSE'] },
  // { value: 'LEVER', label: JOB_SOURCE_LABELS['LEVER'] },
  { value: 'SEEK', label: JOB_SOURCE_LABELS['SEEK'] },
];

export function jobSourceLabel(source?: string): string {
  return JOB_SOURCE_LABELS[source || ''] || source || '';
}

export function jobSourceColor(source?: string): string {
  return JOB_SOURCE_COLORS[source || ''] || '#667eea';
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
