import { AfterViewInit, Component, ElementRef, inject, OnInit, signal, viewChild } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';

import { TranslateModule } from '@ngx-translate/core';

import { environment } from 'environments/environment';
import { AccountService } from 'app/core/auth/account.service';
import { LoginService } from 'app/login/login.service';
import { TranslateDirective } from 'app/shared/language';

@Component({
  selector: 'jhi-login',
  imports: [TranslateDirective, TranslateModule, ReactiveFormsModule, RouterLink],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export default class LoginComponent implements OnInit, AfterViewInit {
  username = viewChild.required<ElementRef>('username');

  authenticationError = signal(false);

  loginForm = new FormGroup({
    username: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    password: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    rememberMe: new FormControl(false, { nonNullable: true, validators: [Validators.required] }),
  });

  private readonly accountService = inject(AccountService);
  private readonly loginService = inject(LoginService);
  private readonly router = inject(Router);
  private readonly http = inject(HttpClient);

  ngOnInit(): void {
    // if already authenticated then navigate to home page (or onboarding on first login)
    this.accountService.identity().subscribe(account => {
      if (account) {
        this.router.navigate([account.onboardingCompleted ? '' : '/onboarding']);
      }
    });
  }

  ngAfterViewInit(): void {
    this.username().nativeElement.focus();
  }

  login(): void {
    this.loginService.login(this.loginForm.getRawValue()).subscribe({
      next: account => {
        this.authenticationError.set(false);
        if (account && !account.onboardingCompleted) {
          // First login: send the user through the onboarding wizard.
          this.router.navigate(['/onboarding']);
        } else if (!this.router.currentNavigation()) {
          // There were no routing during login (eg from navigationToStoredUrl)
          this.router.navigate(['']);
        }
      },
      error: () => this.authenticationError.set(true),
    });
  }

  loginWithGoogle(): void {
    this.http.get<{ url: string }>('/api/google-drive/oauth/login-url').subscribe({
      next({ url }) {
        window.location.href = url;
      },
      error() {
        // fallback direct redirect si l'endpoint échoue
        window.location.href = '/api/google-drive/oauth/login';
      },
    });
  }
}
