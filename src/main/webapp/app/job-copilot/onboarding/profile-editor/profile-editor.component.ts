import { ChangeDetectionStrategy, Component, computed, model } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateModule } from '@ngx-translate/core';
import { TranslateDirective } from 'app/shared/language';

import { CandidateProfile } from '../../shared/job-copilot.model';
import {
  CandidateProfileCertificationDto,
  CandidateProfileEducationDto,
  CandidateProfileExperienceDto,
} from '../../cv-builder/candidate-profile.mapper';
import { ChipsInputComponent } from '../../shared/chips-input/chips-input.component';

/**
 * Editable view of the extracted candidate profile. Signal-first: the profile is a two-way
 * {@link model} and every mutation is an immutable {@code update()} on it, so the parent's signal
 * stays the single source of truth. Skills are edited by name (level preserved for existing ones).
 */
@Component({
  selector: 'jhi-profile-editor',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, FaIconComponent, TranslateModule, TranslateDirective, ChipsInputComponent],
  templateUrl: './profile-editor.component.html',
  styleUrl: './profile-editor.component.scss',
})
export class ProfileEditorComponent {
  readonly profile = model.required<CandidateProfile>();

  /** Skill names, for the chips editor (the underlying SkillDto keeps its level). */
  readonly skillNames = computed(() => (this.profile().skills ?? []).map(s => s.name ?? '').filter(name => name.length > 0));
  readonly roles = computed(() => this.profile().preferredRoles ?? []);
  readonly experiences = computed(() => this.profile().experiences ?? []);
  readonly education = computed(() => this.profile().education ?? []);
  readonly certifications = computed(() => this.profile().certifications ?? []);

  patch(partial: Partial<CandidateProfile>): void {
    this.profile.update(p => ({ ...p, ...partial }));
  }

  onSkillsChange(names: string[]): void {
    this.profile.update(p => {
      const existing = p.skills ?? [];
      const skills = names.map(name => existing.find(s => s.name === name) ?? { name, level: 3 });
      return { ...p, skills };
    });
  }

  onRolesChange(roles: string[]): void {
    this.patch({ preferredRoles: roles });
  }

  // --- Experiences ---------------------------------------------------------------------------
  addExperience(): void {
    this.profile.update(p => ({ ...p, experiences: [...(p.experiences ?? []), { jobTitle: '', company: '' }] }));
  }

  removeExperience(index: number): void {
    this.profile.update(p => ({ ...p, experiences: (p.experiences ?? []).filter((_, i) => i !== index) }));
  }

  patchExperience(index: number, partial: Partial<CandidateProfileExperienceDto>): void {
    this.profile.update(p => ({
      ...p,
      experiences: (p.experiences ?? []).map((e, i) => (i === index ? { ...e, ...partial } : e)),
    }));
  }

  // --- Education -----------------------------------------------------------------------------
  addEducation(): void {
    this.profile.update(p => ({ ...p, education: [...(p.education ?? []), { degree: '', school: '' }] }));
  }

  removeEducation(index: number): void {
    this.profile.update(p => ({ ...p, education: (p.education ?? []).filter((_, i) => i !== index) }));
  }

  patchEducation(index: number, partial: Partial<CandidateProfileEducationDto>): void {
    this.profile.update(p => ({
      ...p,
      education: (p.education ?? []).map((e, i) => (i === index ? { ...e, ...partial } : e)),
    }));
  }

  // --- Certifications ------------------------------------------------------------------------
  addCertification(): void {
    this.profile.update(p => ({ ...p, certifications: [...(p.certifications ?? []), { name: '', issuer: '' }] }));
  }

  removeCertification(index: number): void {
    this.profile.update(p => ({ ...p, certifications: (p.certifications ?? []).filter((_, i) => i !== index) }));
  }

  patchCertification(index: number, partial: Partial<CandidateProfileCertificationDto>): void {
    this.profile.update(p => ({
      ...p,
      certifications: (p.certifications ?? []).map((c, i) => (i === index ? { ...c, ...partial } : c)),
    }));
  }
}
