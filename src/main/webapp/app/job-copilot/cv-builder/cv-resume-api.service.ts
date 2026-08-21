import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { map, Observable } from 'rxjs';
import { CandidateProfileDto } from './candidate-profile.mapper';
import { CvReview } from '../shared/job-copilot.model';

/** Stored resume envelope (server side). `data` is the full Resume JSON, stringified. */
export interface ResumeApiDto {
  id?: string;
  title: string;
  template: string;
  data: string;
  version?: number;
  updatedAt?: string;
}

export interface ResumeVersionApiDto {
  id: string;
  version: number;
  title: string;
  template: string;
  createdAt: string;
}

export type RewriteKind = 'summary' | 'experience' | 'project';

export interface RewriteRequest {
  text: string;
  kind: RewriteKind;
  jobTitle?: string;
  company?: string;
}

export interface RewriteResponse {
  professional: string;
  concise: string;
  ats: string;
}

// Sourced from the shared model; re-exported so existing imports keep working.
import { CvMatchResult } from '../shared/job-copilot.model';
export type { CvMatchResult };

/** Structured resume produced by POST /generate. Mirrors the backend GeneratedResumeDto. */
export interface GeneratedResume {
  personal: {
    firstName: string;
    lastName: string;
    title: string;
    email: string;
    phone: string;
    location: string;
    linkedin: string;
    github: string;
    website: string;
  };
  summary: string;
  experiences: {
    jobTitle: string;
    company: string;
    startDate: string;
    endDate: string;
    current: boolean;
    description: string;
    achievements: string[];
  }[];
  education: { degree: string; school: string; startDate: string; endDate: string; description: string }[];
  projects: { name: string; description: string; url: string; technologies: string[] }[];
  certifications: { name: string; issuer: string; date: string }[];
  skills: { name: string; level: number }[];
  languages: { name: string; level: string }[];
}

@Injectable({ providedIn: 'root' })
export class CvResumeApiService {
  private http = inject(HttpClient);
  private readonly url = 'api/cv-builder';

  /** Returns the current user's resume, or null when none is stored yet (HTTP 204). */
  getResume(): Observable<ResumeApiDto | null> {
    return this.http
      .get<ResumeApiDto>(`${this.url}/resume`, { observe: 'response' })
      .pipe(map(res => (res.status === 204 ? null : res.body)));
  }

  saveResume(dto: ResumeApiDto): Observable<ResumeApiDto> {
    return this.http.put<ResumeApiDto>(`${this.url}/resume`, dto);
  }

  createVersion(): Observable<ResumeVersionApiDto> {
    return this.http.post<ResumeVersionApiDto>(`${this.url}/resume/versions`, {});
  }

  listVersions(): Observable<ResumeVersionApiDto[]> {
    return this.http.get<ResumeVersionApiDto[]>(`${this.url}/resume/versions`);
  }

  getVersion(id: string): Observable<ResumeApiDto> {
    return this.http.get<ResumeApiDto>(`${this.url}/resume/versions/${id}`);
  }

  restoreVersion(id: string): Observable<ResumeApiDto> {
    return this.http.post<ResumeApiDto>(`${this.url}/resume/versions/${id}/restore`, {});
  }

  /** Rewrites a CV free-text field into 3 AI variants (professional / concise / ATS). */
  rewrite(request: RewriteRequest): Observable<RewriteResponse> {
    return this.http.post<RewriteResponse>(`${this.url}/rewrite`, request);
  }

  /** AI review of the whole resume (score + strengths + improvements). */
  reviewResume(content: string): Observable<CvReview> {
    return this.http.post<CvReview>(`${this.url}/review`, { content });
  }

  /** Matches the resume against a pasted job offer (scores + missing skills/keywords + suggestions). */
  matchResume(content: string, offer: string): Observable<CvMatchResult> {
    return this.http.post<CvMatchResult>(`${this.url}/match`, { content, offer });
  }

  /** Generates a full structured resume from free-form source text (+ optional target offer). */
  generateResume(source: string, offer?: string): Observable<GeneratedResume> {
    return this.http.post<GeneratedResume>(`${this.url}/generate`, { source, offer: offer ?? '' });
  }

  /** Translates an existing resume JSON into the target language. */
  translateResume(source: string, language: string): Observable<GeneratedResume> {
    return this.http.post<GeneratedResume>(`${this.url}/translate`, { source, language });
  }

  /** The user's extracted candidate profile, or null when none exists yet (HTTP 204). */
  /**
   * Structures the CV the user uploaded into the builder's model.
   *
   * Preferred over {@link getCandidateProfile} for prefilling: the candidate profile has no projects,
   * no summary, no phone and no links, so seeding from it silently drops those sections. Resolves to
   * null when the user has never uploaded a CV (204).
   */
  importFromUploadedCv(): Observable<GeneratedResume | null> {
    return this.http
      .post<GeneratedResume>(`${this.url}/resume/import-from-cv`, {}, { observe: 'response' })
      .pipe(map(res => (res.status === 204 ? null : res.body)));
  }

  getCandidateProfile(): Observable<CandidateProfileDto | null> {
    return this.http
      .get<CandidateProfileDto>(`${this.url}/resume/candidate-profile`, { observe: 'response' })
      .pipe(map(res => (res.status === 204 ? null : res.body)));
  }
}
