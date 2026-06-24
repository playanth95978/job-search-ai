import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { Observable } from 'rxjs';
import {
  ApplicationStats,
  ApplyOutcome,
  CandidateProfile,
  CvMatchResult,
  ChatMessageHistory,
  CvReview,
  InterviewMessage,
  JobApplication,
  JobOffer,
  MatchResult,
  UserPreference,
  UserPreferenceDto,
} from './job-copilot.model';
import { createRequestOption } from '../../core/request/request-util';
import { Resume } from '../cv-builder/resume.model';
import { GeneratedResume } from '../cv-builder/cv-resume-api.service';

@Injectable({ providedIn: 'root' })
export class JobCopilotService {
  private http = inject(HttpClient);
  private translateService = inject(TranslateService);
  private baseUrl = 'api/job-copilot';

  // CV
  uploadCv(file: File): Observable<CandidateProfile> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<CandidateProfile>(`${this.baseUrl}/cv/upload`, formData);
  }

  getProfile(): Observable<CandidateProfile> {
    return this.http.get<CandidateProfile>(`${this.baseUrl}/cv/profile`);
  }

  geResumeProfile(): Observable<Resume> {
    return this.http.get<Resume>(`${this.baseUrl}/cv/profile`);
  }

  reviewCv(language?: string): Observable<CvReview> {
    const lang = language ?? this.translateService.getCurrentLang();
    return this.http.post<CvReview>(`api/cv-builder/review`, { content: '', language: lang });
  }

  rewriteCv(text: string, kind: string, jobTitle?: string, company?: string, language?: string): Observable<any> {
    const lang = language ?? this.translateService.getCurrentLang();
    return this.http.post(`api/cv-builder/rewrite`, { text, kind, jobTitle, company, language: lang });
  }

  matchCv(content: string, offer: string, language?: string): Observable<CvMatchResult> {
    const lang = language ?? this.translateService.getCurrentLang();
    return this.http.post<CvMatchResult>(`api/cv-builder/match`, { content, offer, language: lang });
  }

  generateResume(source: string, offer?: string, language?: string): Observable<any> {
    const lang = language ?? this.translateService.getCurrentLang();
    return this.http.post(`api/cv-builder/generate`, { source, offer, language: lang });
  }

  // Jobs
  searchJobs(keywords?: string, location?: string, source?: string): Observable<JobOffer[]> {
    let params = new HttpParams();
    if (keywords) params = params.set('keywords', keywords);
    if (location) params = params.set('location', location);
    if (source) params = params.set('source', source);
    return this.http.get<JobOffer[]>(`${this.baseUrl}/jobs/search`, { params });
  }

  fetchFromUrl(url: string): Observable<JobOffer[]> {
    return this.http.post<JobOffer[]>(`${this.baseUrl}/jobs/fetch`, null, { params: { url } });
  }

  /** Lazily fetches the full description of a list (STUB) offer and queues it for embedding. */
  enrichJob(id: string): Observable<JobOffer> {
    return this.http.post<JobOffer>(`${this.baseUrl}/jobs/${id}/enrich`, null);
  }

  matchJobs(topK?: number): Observable<MatchResult[]> {
    let params = new HttpParams();
    if (topK != null) params = params.set('topK', topK);
    return this.http.get<MatchResult[]>(`${this.baseUrl}/jobs/match`, { params });
  }

  matchAdvanced(): Observable<MatchResult[]> {
    return this.http.get<MatchResult[]>(`${this.baseUrl}/jobs/match-advanced`);
  }

  // Positioning (CV vs offer)
  /** AI positioning of the current user's CV against an offer (cached server-side per user+offer). */
  getPositioning(offerId: string, refresh = false, language?: string): Observable<CvMatchResult> {
    let params = new HttpParams();
    if (refresh) params = params.set('refresh', 'true');
    const lang = language ?? this.translateService.getCurrentLang();
    if (lang) params = params.set('language', lang);
    return this.http.get<CvMatchResult>(`${this.baseUrl}/jobs/${offerId}/positioning`, { params });
  }

  /** "What-if" re-scoring: re-evaluate the offer as if the candidate had these extra skills. */
  simulatePositioning(offerId: string, additions: string[], language?: string): Observable<CvMatchResult> {
    let params = new HttpParams();
    const lang = language ?? this.translateService.getCurrentLang();
    if (lang) params = params.set('language', lang);
    return this.http.post<CvMatchResult>(`${this.baseUrl}/jobs/${offerId}/positioning/simulate`, { additions }, { params });
  }

  /** ATS-optimized variant of the user's resume tailored to this offer (cached server-side). */
  getTailoredResume(offerId: string, refresh = false): Observable<GeneratedResume> {
    let params = new HttpParams();
    if (refresh) params = params.set('refresh', 'true');
    return this.http.get<GeneratedResume>(`${this.baseUrl}/jobs/${offerId}/tailored`, { params });
  }

  // Applications
  getApplications(): Observable<JobApplication[]> {
    return this.http.get<JobApplication[]>(`${this.baseUrl}/applications`);
  }

  getApplicationStats(): Observable<ApplicationStats> {
    return this.http.get<ApplicationStats>(`${this.baseUrl}/applications/stats`);
  }

  createApplication(jobOfferId: string, generateCoverLetter = true, language?: string): Observable<JobApplication> {
    let params = new HttpParams().set('jobOfferId', jobOfferId).set('generateCoverLetter', String(generateCoverLetter));
    const lang = language ?? this.translateService.getCurrentLang();
    if (lang) params = params.set('language', lang);
    return this.http.post<JobApplication>(`${this.baseUrl}/applications/create`, null, { params });
  }

  updateApplicationStatus(id: string, status: string): Observable<JobApplication> {
    return this.http.put<JobApplication>(`${this.baseUrl}/applications/${id}/status`, null, { params: { status } });
  }

  generateCoverLetter(jobOfferId: string, language?: string): Observable<string> {
    let params = new HttpParams();
    const lang = language ?? this.translateService.getCurrentLang();
    if (lang) params = params.set('language', lang);
    return this.http.post(`${this.baseUrl}/applications/${jobOfferId}/cover-letter`, null, {
      params,
      responseType: 'text',
    });
  }

  updateCoverLetter(applicationId: string, coverLetter: string): Observable<JobApplication> {
    return this.http.put<JobApplication>(`${this.baseUrl}/applications/${applicationId}/cover-letter`, coverLetter, {
      headers: { 'Content-Type': 'text/plain' },
    });
  }

  getAutoApplySources(): Observable<string[]> {
    return this.http.get<string[]>(`${this.baseUrl}/applications/auto-apply-sources`);
  }

  applyToJob(applicationId: string): Observable<ApplyOutcome> {
    return this.http.post<ApplyOutcome>(`${this.baseUrl}/applications/${applicationId}/apply`, null);
  }

  // Interview roleplay
  startInterview(jobOfferId: string, language?: string): Observable<InterviewMessage> {
    let params = new HttpParams().set('jobOfferId', jobOfferId);
    if (language) params = params.set('language', language);
    return this.http.post<InterviewMessage>(`${this.baseUrl}/interview/start`, null, { params });
  }

  sendInterviewAnswer(conversationId: string, jobOfferId: string, answer: string, language?: string): Observable<InterviewMessage> {
    let params = new HttpParams().set('jobOfferId', jobOfferId);
    if (language) params = params.set('language', language);
    return this.http.post<InterviewMessage>(`${this.baseUrl}/interview/${conversationId}/message`, answer, {
      params,
      headers: { 'Content-Type': 'text/plain' },
    });
  }

  /** Loads the full message history of a conversation (USER/ASSISTANT/SYSTEM), chronological order. */
  getConversationMessages(conversationId: string): Observable<ChatMessageHistory[]> {
    return this.http.get<ChatMessageHistory[]>(`api/chat-history/history/${encodeURIComponent(conversationId)}`);
  }

  // Preferences
  getPreferences(): Observable<UserPreference> {
    return this.http.get<UserPreference>(`${this.baseUrl}/preferences`);
  }

  updatePreferences(dto: UserPreferenceDto): Observable<UserPreference> {
    return this.http.put<UserPreference>(`${this.baseUrl}/preferences`, dto);
  }

  query(param: any): Observable<HttpResponse<JobOffer[]>> {
    const options = createRequestOption(param);
    return this.http.get<JobOffer[]>(`${this.baseUrl}/jobs/_search`, { params: options, observe: 'response' });
  }
}
