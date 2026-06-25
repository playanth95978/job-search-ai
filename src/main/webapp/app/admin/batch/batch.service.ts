import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

import { Observable } from 'rxjs';

import { ApplicationConfigService } from 'app/core/config/application-config.service';

import { JobExecution, JobSummary } from './batch.model';

@Injectable({ providedIn: 'root' })
export class BatchService {
  private readonly http = inject(HttpClient);
  private readonly applicationConfigService = inject(ApplicationConfigService);

  private readonly resourceUrl = this.applicationConfigService.getEndpointFor('api/admin/batch');

  jobs(): Observable<JobSummary[]> {
    return this.http.get<JobSummary[]>(`${this.resourceUrl}/jobs`);
  }

  executions(jobName: string): Observable<JobExecution[]> {
    return this.http.get<JobExecution[]>(`${this.resourceUrl}/jobs/${jobName}/executions`);
  }

  execute(jobName: string): Observable<void> {
    return this.http.post<void>(`${this.resourceUrl}/jobs/${jobName}/execute`, null);
  }
}
