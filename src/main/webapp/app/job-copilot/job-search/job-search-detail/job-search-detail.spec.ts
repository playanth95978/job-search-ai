import { ComponentFixture, TestBed } from '@angular/core/testing';

import { JobSearchDetail } from './job-search-detail';

describe('JobSearchDetail', () => {
  let component: JobSearchDetail;
  let fixture: ComponentFixture<JobSearchDetail>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [JobSearchDetail],
    }).compileComponents();

    fixture = TestBed.createComponent(JobSearchDetail);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
