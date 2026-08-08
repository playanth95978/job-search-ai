import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateModule } from '@ngx-translate/core';

import { TranslateDirective } from 'app/shared/language';
import { JOB_COPILOT_NAV } from 'app/job-copilot/shared/job-copilot-nav';
import { JobNavService } from './job-nav.service';

/**
 * Left navigation rail (JobTogether-style) linking to the Job Copilot pages. Collapsible on desktop
 * (icons ↔ icons+labels, persisted) and an off-canvas drawer on mobile. Rendered globally for
 * authenticated users by the main layout. State lives in {@link JobNavService} so the layout can
 * shift its content to match the rail width.
 */
@Component({
  selector: 'jhi-job-nav',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterLinkActive, FaIconComponent, TranslateModule, TranslateDirective],
  templateUrl: './job-nav.component.html',
  styleUrl: './job-nav.component.scss',
})
export class JobNavComponent {
  protected readonly nav = inject(JobNavService);
  protected readonly items = JOB_COPILOT_NAV;
}
