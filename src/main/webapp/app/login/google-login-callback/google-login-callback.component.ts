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
      const code = params['code'];
      if (code) {
        this.http.post<any>('/api/google-drive/oauth/google', { code }).subscribe({
          next: response => {
            const jwt = response.id_token;
            this.authServerProvider.authenticateSuccess({ id_token: jwt }, false);
            this.accountService.identity(true).subscribe(() => {
              this.router.navigate(['/']);
            });
          },
          error: () => {
            this.router.navigate(['/login'], { queryParams: { error: true } });
          },
        });
      } else {
        this.router.navigate(['/login']);
      }
    });
  }
}
