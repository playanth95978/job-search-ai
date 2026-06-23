import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateModule } from '@ngx-translate/core';
import { TranslateDirective } from 'app/shared/language';
import { Account } from 'app/core/auth/account.model';

@Component({
  selector: 'jhi-home-banniere',
  standalone: true,
  imports: [TranslateDirective, TranslateModule, RouterLink, FaIconComponent],
  templateUrl: './home-banniere.html',
  styleUrl: './home-banniere.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomeBanniere {
  account = input<Account | null>(null);
  loginAction = output<void>();

  login(): void {
    this.loginAction.emit();
  }
}
