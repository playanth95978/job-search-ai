import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

import { Observable } from 'rxjs';

import { ApplicationConfigService } from 'app/core/config/application-config.service';
import { createRequestOption } from 'app/core/request/request-util';

import { JobExecution, JobSummary } from './batch.model';

@Injectable({ providedIn: 'root' })
export class BatchService {
  private readonly http = inject(HttpClient);
  private readonly applicationConfigService = inject(ApplicationConfigService);

  private readonly resourceUrl = this.applicationConfigService.getEndpointFor('api/admin/batch');

  jobs(): Observable<JobSummary[]> {
    return this.http.get<JobSummary[]>(`${this.resourceUrl}/jobs`);
  }

  executions(jobName: string, req?: any): Observable<HttpResponse<JobExecution[]>> {
    const options = createRequestOption(req);
    return this.http.get<JobExecution[]>(`${this.resourceUrl}/jobs/${jobName}/executions`, { params: options, observe: 'response' });
  }

  execute(jobName: string): Observable<void> {
    return this.http.post<void>(`${this.resourceUrl}/jobs/${jobName}/execute`, null);
  }
}
