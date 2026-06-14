import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { map, Observable } from 'rxjs';
import { CandidateProfileDto } from './candidate-profile.mapper';

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

  /** The user's extracted candidate profile, or null when none exists yet (HTTP 204). */
  getCandidateProfile(): Observable<CandidateProfileDto | null> {
    return this.http
      .get<CandidateProfileDto>(`${this.url}/resume/candidate-profile`, { observe: 'response' })
      .pipe(map(res => (res.status === 204 ? null : res.body)));
  }
}
