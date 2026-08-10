import { beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateModule } from '@ngx-translate/core';
import { FaIconLibrary } from '@fortawesome/angular-fontawesome';

import { fontAwesomeIcons } from 'app/config/font-awesome-icons';
import { CvBuilderComponent } from './cv-builder.component';
import { ResumeStore } from './resume-store';
import { createEmptyResume, Resume } from './resume.model';

/**
 * Smoke test for the CV Builder route: creating and rendering the component must not throw.
 *
 * A construction-time error here is invisible to `ng build` and surfaces in the browser only as a
 * blank page, so it is worth catching in CI rather than by hand.
 */
describe('CvBuilderComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [TranslateModule.forRoot()],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    // `App` registers these at bootstrap; without them fa-icon throws on the first icon it meets and
    // the render stops there, which would hide anything broken further down the page.
    TestBed.inject(FaIconLibrary).addIcons(...fontAwesomeIcons);
  });

  it('renders the builder without throwing', () => {
    const fixture = TestBed.createComponent(CvBuilderComponent);
    fixture.detectChanges();

    expect(fixture.componentInstance).toBeTruthy();
    expect(fixture.nativeElement.textContent).not.toBe('');
  });

  it('seeds itself from the uploaded CV when no resume has been saved yet', () => {
    // The first-login path: onboarding extracted a candidate profile, but the user has never opened
    // the builder, so GET /resume answers 204. The builder used to show an empty form here.
    const store = TestBed.inject(ResumeStore);
    store.reset();
    const fixture = TestBed.createComponent(CvBuilderComponent);
    const http = TestBed.inject(HttpTestingController);

    http.expectOne('api/cv-builder/resume').flush(null, { status: 204, statusText: 'No Content' });
    http.expectOne('api/cv-builder/resume/candidate-profile').flush({
      fullName: 'Jane Doe',
      email: 'jane@example.com',
      location: 'Nouméa',
      skills: [{ name: 'Java', level: 5 }],
      experiences: [{ jobTitle: 'Staff Engineer', company: 'Acme Corp' }],
    });
    fixture.detectChanges();

    expect(store.resume().personal.firstName).toBe('Jane');
    expect(store.resume().personal.lastName).toBe('Doe');
    expect(store.resume().skills.map(s => s.name)).toContain('Java');
    expect(store.resume().experiences[0].company).toBe('Acme Corp');
  });

  it('never overwrites a local draft that already has content', () => {
    const store = TestBed.inject(ResumeStore);
    store.load(populatedResume());
    TestBed.createComponent(CvBuilderComponent);
    const http = TestBed.inject(HttpTestingController);

    http.expectOne('api/cv-builder/resume').flush(null, { status: 204, statusText: 'No Content' });
    http.expectOne('api/cv-builder/resume/candidate-profile').flush({ fullName: 'Someone Else', email: 'other@example.com' });

    expect(store.resume().personal.firstName).toBe('Jane');
  });

  it('prefers the saved resume over the extracted profile', () => {
    const store = TestBed.inject(ResumeStore);
    store.reset();
    TestBed.createComponent(CvBuilderComponent);
    const http = TestBed.inject(HttpTestingController);

    const saved = { ...populatedResume(), summary: 'From the server' };
    http.expectOne('api/cv-builder/resume').flush({ title: 'Mon CV', template: 'modern', data: JSON.stringify(saved) });
    http.expectNone('api/cv-builder/resume/candidate-profile');

    expect(store.resume().summary).toBe('From the server');
  });

  it('renders a populated resume without throwing', () => {
    // The empty-state render exercises only a fraction of the template; a filled CV opens the
    // per-entry branches of the editor and of all four preview templates.
    TestBed.inject(ResumeStore).load(populatedResume());
    const fixture = TestBed.createComponent(CvBuilderComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Jane');
  });
});

function populatedResume(): Resume {
  return {
    ...createEmptyResume(),
    personal: {
      firstName: 'Jane',
      lastName: 'Doe',
      title: 'Staff Engineer',
      email: 'jane@example.com',
      phone: '+687 12 34 56',
      location: 'Nouméa',
      linkedin: 'linkedin.com/in/janedoe',
      github: 'github.com/janedoe',
      website: 'jane.example',
      workingRights: 'Full working rights',
    },
    summary: 'Backend engineer with ten years on distributed search systems.',
    experiences: [
      {
        id: 'e1',
        jobTitle: 'Staff Engineer',
        company: 'Acme Corp',
        startDate: 'Jan 2021',
        endDate: '',
        current: true,
        description: 'Owned the search platform end to end.',
        achievements: ['Cut p99 latency by 40%'],
      },
    ],
    education: [
      { id: 'ed1', degree: 'MSc Computer Science', school: 'Université de Lorraine', startDate: '2008', endDate: '2010', description: '' },
    ],
    projects: [{ id: 'p1', name: 'Vector Search Engine', description: 'Hybrid retrieval service.', url: '', technologies: ['Java'] }],
    certifications: [{ id: 'c1', name: 'AWS Solutions Architect', issuer: 'Amazon', date: '2023' }],
    skills: [{ id: 's1', name: 'Java', level: 5 }],
    languages: [{ id: 'l1', name: 'French', level: 'Native' }],
    referees: [
      { id: 'r1', name: 'Erin Blake', position: 'Head of Engineering', company: 'Acme Corp', email: 'erin@example.com', phone: '' },
    ],
  };
}
