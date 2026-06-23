import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HomeBanniere } from './home-banniere';
import { TranslateModule } from '@ngx-translate/core';
import { RouterModule } from '@angular/router';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';

describe('HomeBanniere Component', () => {
  let comp: HomeBanniere;
  let fixture: ComponentFixture<HomeBanniere>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HomeBanniere, TranslateModule.forRoot(), RouterModule.forRoot([]), FontAwesomeModule],
    }).compileComponents();

    fixture = TestBed.createComponent(HomeBanniere);
    comp = fixture.componentInstance;
  });

  it('should create', () => {
    expect(comp).toBeTruthy();
  });

  it('should emit loginAction when login is called', () => {
    const emitSpy = vitest.spyOn(comp.loginAction, 'emit');
    comp.login();
    expect(emitSpy).toHaveBeenCalled();
  });
});
