import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { AuthServerProvider } from 'app/core/auth/auth-jwt.service';
import { AccountService } from 'app/core/auth/account.service';

@Component({
  selector: 'jhi-google-login-callback',
  standalone: true,
  template:
    '<div class="d-flex justify-content-center mt-5"><div class="spinner-border" role="status"><span class="visually-hidden">Loading...</span></div></div>',
})
export class GoogleLoginCallbackComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly http = inject(HttpClient);
  private readonly authServerProvider = inject(AuthServerProvider);
  private readonly accountService = inject(AccountService);

  ngOnInit(): void {
    this.route.queryParams.subscribe(params => {
      // Google returns ?error=access_denied when the user cancels
      if (params['error']) {
        this.router.navigate(['/login']);
        return;
      }

      const code = params['code'];
      if (!code) {
        this.router.navigate(['/login']);
        return;
      }

      this.http.post<{ id_token: string }>('/api/google-drive/oauth/google', { code }).subscribe({
        next: response => {
          // rememberMe = true : token stocké dans localStorage pour persistance inter-sessions
          this.authServerProvider.authenticateSuccess({ id_token: response.id_token }, true);
          this.accountService.identity(true).subscribe(() => {
            this.router.navigate(['/']);
          });
        },
        error: () => {
          this.router.navigate(['/login'], { queryParams: { error: true } });
        },
      });
    });
  }
}
