import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateModule } from '@ngx-translate/core';

import { TranslateDirective } from 'app/shared/language';

import { JobCopilotService } from '../shared/job-copilot.service';
import { UserPreferenceDto } from '../shared/job-copilot.model';

@Component({
  selector: 'jhi-job-copilot-settings',
  standalone: true,
  imports: [CommonModule, FormsModule, FaIconComponent, TranslateDirective, TranslateModule],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.scss',
})
export class SettingsComponent implements OnInit {
  prefs: Partial<UserPreferenceDto> = {};
  rolesInput = '';
  excludedInput = '';
  locationsInput = '';
  saving = signal(false);
  saved = signal(false);

  private service = inject(JobCopilotService);

  ngOnInit(): void {
    this.service.getPreferences().subscribe({
      next: p => {
        this.prefs = {
          remoteOnly: p.remoteOnly,
          contractType: p.contractType ?? '',
          salaryMin: p.salaryMin,
          salaryMax: p.salaryMax,
        };
        this.rolesInput = this.parseJson(p.preferredRoles).join(', ');
        this.excludedInput = this.parseJson(p.excludedTechnologies).join(', ');
        this.locationsInput = this.parseJson(p.preferredLocations).join(', ');
      },
    });
  }

  save(): void {
    this.saving.set(true);
    this.saved.set(false);
    const dto: UserPreferenceDto = {
      remoteOnly: this.prefs.remoteOnly ?? false,
      contractType: this.prefs.contractType ?? '',
      salaryMin: this.prefs.salaryMin ?? undefined,
      salaryMax: this.prefs.salaryMax ?? undefined,
      preferredRoles: this.splitInput(this.rolesInput),
      excludedTechnologies: this.splitInput(this.excludedInput),
      preferredLocations: this.splitInput(this.locationsInput),
    };
    this.service.updatePreferences(dto).subscribe({
      next: () => {
        this.saving.set(false);
        this.saved.set(true);
      },
      error: () => this.saving.set(false),
    });
  }

  private splitInput(input: string): string[] {
    return input
      .split(',')
      .map(s => s.trim())
      .filter(s => s.length > 0);
  }

  private parseJson(json?: string): string[] {
    if (!json) return [];
    try {
      const parsed: unknown = JSON.parse(json);
      return Array.isArray(parsed) ? parsed.filter((item): item is string => typeof item === 'string') : [];
    } catch {
      return [];
    }
  }
}
