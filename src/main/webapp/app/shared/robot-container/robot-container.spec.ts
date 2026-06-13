import { ComponentFixture, TestBed } from '@angular/core/testing';

import { RobotContainer } from './robot-container';

describe('RobotContainer', () => {
  let component: RobotContainer;
  let fixture: ComponentFixture<RobotContainer>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RobotContainer],
    }).compileComponents();

    fixture = TestBed.createComponent(RobotContainer);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
