import { computed, inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { Observable } from 'rxjs';
import {
  ApplicationStats,
  AssistantMessage,
  AutopsyReport,
  BoardSuggestion,
  CandidateProfile,
  CareerRoadmap,
  CvMatchResult,
  ChatMessageHistory,
  CvReview,
  AtsSimulation,
  InterviewMessage,
  JobApplication,
  JobOffer,
  MatchResult,
  PanelMessage,
  PanelReport,
  RadarHit,
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

  lang = computed(() => this.translateService.getCurrentLang());

  // CV
  uploadCv(file: File): Observable<CandidateProfile> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<CandidateProfile>(`${this.baseUrl}/cv/upload`, formData);
  }

  getProfile(): Observable<CandidateProfile> {
    return this.http.get<CandidateProfile>(`${this.baseUrl}/cv/profile`);
  }

  /** Persists user edits to the extracted profile (identity, skills, roles, experiences…). */
  updateProfile(profile: CandidateProfile): Observable<CandidateProfile> {
    return this.http.put<CandidateProfile>(`${this.baseUrl}/cv/profile`, profile);
  }

  geResumeProfile(): Observable<Resume> {
    return this.http.get<Resume>(`${this.baseUrl}/cv/profile`);
  }

  reviewCv(language?: string): Observable<CvReview> {
    // Reviews the ingested CV (the profile's stored markdown), not an empty editor buffer.
    const lang = language ?? this.lang();
    const params = new HttpParams().set('language', lang);
    return this.http.get<CvReview>(`${this.baseUrl}/cv/review`, { params });
  }

  simulateAtsParsing(language?: string): Observable<AtsSimulation> {
    const lang = language ?? this.lang();
    const params = new HttpParams().set('language', lang);
    return this.http.get<AtsSimulation>(`${this.baseUrl}/cv/ats-simulation`, { params });
  }

  simulateCareer(targetRole: string, horizonYears: number, language?: string): Observable<CareerRoadmap> {
    const lang = language ?? this.lang();
    const params = new HttpParams().set('language', lang);
    return this.http.post<CareerRoadmap>(`${this.baseUrl}/career/simulate`, { targetRole, horizonYears }, { params });
  }

  rewriteCv(text: string, kind: string, jobTitle?: string, company?: string, language?: string): Observable<any> {
    const lang = language ?? this.lang();
    return this.http.post(`api/cv-builder/rewrite`, { text, kind, jobTitle, company, language: lang });
  }

  matchCv(content: string, offer: string, language?: string): Observable<CvMatchResult> {
    const lang = language ?? this.lang();
    return this.http.post<CvMatchResult>(`api/cv-builder/match`, { content, offer, language: lang });
  }

  generateResume(source: string, offer?: string, language?: string): Observable<any> {
    const lang = language ?? this.lang();
    return this.http.post(`api/cv-builder/generate`, { source, offer, language: lang });
  }

  // Jobs
  searchJobs(keywords?: string, location?: string, source?: string, country?: string): Observable<JobOffer[]> {
    let params = new HttpParams();
    if (keywords) params = params.set('keywords', keywords);
    if (location) params = params.set('location', location);
    if (source) params = params.set('source', source);
    if (country) params = params.set('country', country);
    return this.http.get<JobOffer[]>(`${this.baseUrl}/jobs/search`, { params });
  }

  /**
   * Semantic search over the ingested corpus (hybrid vector + BM25 + RRF + reranking).
   * Instant and meaning-aware; does not hit external boards live.
   */
  searchSmart(query: string, location?: string, source?: string, limit = 30): Observable<JobOffer[]> {
    let params = new HttpParams().set('query', query).set('limit', limit);
    if (location) params = params.set('location', location);
    if (source) params = params.set('source', source);
    return this.http.get<JobOffer[]>(`${this.baseUrl}/jobs/search-smart`, { params });
  }

  /** Public count of offers actually indexed (embedding COMPLETED), for the home "corpus size" stat. */
  getIndexedJobCount(): Observable<{ count: number }> {
    return this.http.get<{ count: number }>(`${this.baseUrl}/jobs/indexed-count`);
  }

  /**
   * Latest live board opportunities (Adzuna / France Travail) matched to the signed-in user's
   * profile, for the home feed. Cached server-side per user; pass refresh to bypass that cache.
   */
  getLiveOpportunities(refresh = false): Observable<JobOffer[]> {
    let params = new HttpParams();
    if (refresh) params = params.set('refresh', 'true');
    return this.http.get<JobOffer[]>(`${this.baseUrl}/opportunities/live`, { params });
  }

  /**
   * Boards ATS suggérés à partir du profil : les noms d'entreprise viennent du modèle, mais chaque
   * entrée renvoyée a été résolue contre l'API réelle de l'ATS — slug et URL sont vérifiés.
   */
  suggestBoards(limit = 6): Observable<BoardSuggestion[]> {
    return this.http.get<BoardSuggestion[]>(`${this.baseUrl}/jobs/boards/suggestions`, { params: { limit } });
  }

  fetchFromUrl(url: string): Observable<JobOffer[]> {
    return this.http.post<JobOffer[]>(`${this.baseUrl}/jobs/fetch`, null, { params: { url } });
  }

  /** Application Autopsy: what the user's own funnel reveals about why they get (or miss) interviews. */
  getAutopsy(): Observable<AutopsyReport> {
    return this.http.get<AutopsyReport>(`${this.baseUrl}/insights/autopsy`);
  }

  // Opportunity Radar
  getRadar(): Observable<RadarHit[]> {
    return this.http.get<RadarHit[]>(`${this.baseUrl}/radar`);
  }

  radarUnreadCount(): Observable<{ count: number }> {
    return this.http.get<{ count: number }>(`${this.baseUrl}/radar/unread-count`);
  }

  markRadarSeen(id: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/radar/${id}/seen`, null);
  }

  dismissRadar(id: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/radar/${id}/dismiss`, null);
  }

  /** Lazily fetches the full description of a list (STUB) offer and queues it for embedding. */
  enrichJob(id: string): Observable<JobOffer> {
    return this.http.post<JobOffer>(`${this.baseUrl}/jobs/${id}/enrich`, null);
  }

  translateJob(offer: JobOffer, language: string): Observable<JobOffer> {
    const params = new HttpParams().set('language', language);
    if (offer.id) {
      return this.http.post<JobOffer>(`${this.baseUrl}/jobs/${offer.id}/translate`, null, { params });
    } else {
      return this.http.post<JobOffer>(`${this.baseUrl}/jobs/translate`, offer, { params });
    }
  }

  matchJobs(topK?: number, location?: string): Observable<MatchResult[]> {
    let params = new HttpParams();
    if (topK != null) params = params.set('topK', topK);
    if (location) params = params.set('location', location);
    return this.http.get<MatchResult[]>(`${this.baseUrl}/jobs/match`, { params });
  }

  matchAdvanced(location?: string): Observable<MatchResult[]> {
    let params = new HttpParams();
    if (location) params = params.set('location', location);
    return this.http.get<MatchResult[]>(`${this.baseUrl}/jobs/match-advanced`, { params });
  }

  // Positioning (CV vs offer)
  /** AI positioning of the current user's CV against an offer (cached server-side per user+offer). */
  getPositioning(offerId: string, refresh = false, language?: string): Observable<CvMatchResult> {
    let params = new HttpParams();
    if (refresh) params = params.set('refresh', 'true');
    const lang = language ?? this.lang();
    if (lang) params = params.set('language', lang);
    return this.http.get<CvMatchResult>(`${this.baseUrl}/jobs/${offerId}/positioning`, { params });
  }

  /** "What-if" re-scoring: re-evaluate the offer as if the candidate had these extra skills. */
  simulatePositioning(offerId: string, additions: string[], language?: string): Observable<CvMatchResult> {
    let params = new HttpParams();
    const lang = language ?? this.lang();
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
    const lang = language ?? this.lang();
    if (lang) params = params.set('language', lang);
    return this.http.post<JobApplication>(`${this.baseUrl}/applications/create`, null, { params });
  }

  updateApplicationStatus(id: string, status: string): Observable<JobApplication> {
    return this.http.put<JobApplication>(`${this.baseUrl}/applications/${id}/status`, null, { params: { status } });
  }

  generateCoverLetter(jobOfferId: string, language?: string): Observable<string> {
    let params = new HttpParams();
    const lang = language ?? this.lang();
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

  // Shadow Interview Panel (multi-agent jury)
  startPanel(jobOfferId: string, language?: string): Observable<PanelMessage> {
    let params = new HttpParams().set('jobOfferId', jobOfferId);
    if (language) params = params.set('language', language);
    return this.http.post<PanelMessage>(`${this.baseUrl}/panel/start`, null, { params });
  }

  sendPanelAnswer(conversationId: string, jobOfferId: string, answer: string, language?: string): Observable<PanelMessage> {
    let params = new HttpParams().set('jobOfferId', jobOfferId);
    if (language) params = params.set('language', language);
    return this.http.post<PanelMessage>(`${this.baseUrl}/panel/${conversationId}/message`, answer, {
      params,
      headers: { 'Content-Type': 'text/plain' },
    });
  }

  getPanelReport(conversationId: string, jobOfferId: string, language?: string): Observable<PanelReport> {
    let params = new HttpParams().set('jobOfferId', jobOfferId);
    if (language) params = params.set('language', language);
    return this.http.post<PanelReport>(`${this.baseUrl}/panel/${conversationId}/report`, null, { params });
  }

  /** Loads the full message history of a conversation (USER/ASSISTANT/SYSTEM), chronological order. */
  getConversationMessages(conversationId: string): Observable<ChatMessageHistory[]> {
    return this.http.get<ChatMessageHistory[]>(`api/chat-history/history/${encodeURIComponent(conversationId)}`);
  }

  /** Sends a user message to the tool-enabled Job Copilot agent and returns its reply. */
  sendAssistantMessage(conversationId: string, message: string): Observable<AssistantMessage> {
    return this.http.post<AssistantMessage>(`${this.baseUrl}/assistant/${encodeURIComponent(conversationId)}/message`, message, {
      headers: { 'Content-Type': 'text/plain' },
    });
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
