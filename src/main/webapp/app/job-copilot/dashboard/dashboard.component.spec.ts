import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { FaIconLibrary } from '@fortawesome/angular-fontawesome';

import { fontAwesomeIcons } from '../../config/font-awesome-icons';
import { DashboardComponent } from './dashboard.component';
import { JobCopilotService } from '../shared/job-copilot.service';
import { CandidateProfile } from '../shared/job-copilot.model';

describe('DashboardComponent', () => {
  let fixture: ComponentFixture<DashboardComponent>;
  let component: DashboardComponent;

  const mockProfile: CandidateProfile = {
    fullName: 'John Doe',
    email: 'john@example.com',
    location: 'Nouméa',
    yearsOfExperience: 5,
    skills: [{ id: '1', name: 'Angular', level: 4 }],
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [TranslateModule.forRoot()],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    TestBed.inject(FaIconLibrary).addIcons(...fontAwesomeIcons);
  });

  it('renders dashboard with profile when loaded', () => {
    fixture = TestBed.createComponent(DashboardComponent);
    component = fixture.componentInstance;
    const http = TestBed.inject(HttpTestingController);

    fixture.detectChanges();

    http.expectOne('api/job-copilot/radar/unread-count').flush({ count: 2 });
    http.expectOne('api/job-copilot/cv/profile').flush(mockProfile);
    http.expectOne('api/job-copilot/applications/stats').flush({ APPLIED: 3 });

    fixture.detectChanges();

    expect(component.profile()).toEqual(mockProfile);
    expect(component.radarUnread()).toBe(2);
    expect(component.statsEntries()).toEqual([['APPLIED', 3]]);
    expect(fixture.nativeElement.textContent).toContain('John Doe');
  });

  it('toggles dropzone on toggleDropzone call', () => {
    fixture = TestBed.createComponent(DashboardComponent);
    component = fixture.componentInstance;
    const http = TestBed.inject(HttpTestingController);

    fixture.detectChanges();

    http.expectOne('api/job-copilot/radar/unread-count').flush({ count: 0 });
    http.expectOne('api/job-copilot/cv/profile').flush(mockProfile);
    http.expectOne('api/job-copilot/applications/stats').flush({});

    fixture.detectChanges();

    expect(component.showDropzone()).toBe(false);
    component.toggleDropzone();
    expect(component.showDropzone()).toBe(true);
    component.toggleDropzone();
    expect(component.showDropzone()).toBe(false);
  });

  it('uploads CV file and updates profile on the fly', () => {
    fixture = TestBed.createComponent(DashboardComponent);
    component = fixture.componentInstance;
    const http = TestBed.inject(HttpTestingController);

    fixture.detectChanges();

    http.expectOne('api/job-copilot/radar/unread-count').flush({ count: 0 });
    http.expectOne('api/job-copilot/cv/profile').flush(null, { status: 404, statusText: 'Not Found' });
    http.expectOne('api/job-copilot/applications/stats').flush({});

    fixture.detectChanges();
    expect(component.profile()).toBeNull();

    const file = new File(['dummy content'], 'cv.pdf', { type: 'application/pdf' });
    component.upload(file);

    expect(component.uploading()).toBe(true);

    const uploadReq = http.expectOne('api/job-copilot/cv/upload');
    expect(uploadReq.request.method).toBe('POST');
    uploadReq.flush(mockProfile);

    http.expectOne('api/job-copilot/applications/stats').flush({ APPLIED: 1 });

    expect(component.uploading()).toBe(false);
    expect(component.profile()).toEqual(mockProfile);
    expect(component.showDropzone()).toBe(false);
    expect(component.uploadSuccess()).toBe(true);
  });

  it('handles upload error gracefully', () => {
    fixture = TestBed.createComponent(DashboardComponent);
    component = fixture.componentInstance;
    const http = TestBed.inject(HttpTestingController);

    fixture.detectChanges();

    http.expectOne('api/job-copilot/radar/unread-count').flush({ count: 0 });
    http.expectOne('api/job-copilot/cv/profile').flush(null, { status: 404, statusText: 'Not Found' });
    http.expectOne('api/job-copilot/applications/stats').flush({});

    fixture.detectChanges();

    const file = new File(['corrupt content'], 'cv.pdf', { type: 'application/pdf' });
    component.upload(file);

    const uploadReq = http.expectOne('api/job-copilot/cv/upload');
    uploadReq.flush({ message: 'Invalid PDF format' }, { status: 400, statusText: 'Bad Request' });

    expect(component.uploading()).toBe(false);
    expect(component.error()).toBe('Invalid PDF format');
  });
});
