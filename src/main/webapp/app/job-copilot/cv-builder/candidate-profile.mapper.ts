import { Certification, createEmptyResume, Education, Experience, Language, Resume, Skill, uid } from './resume.model';

/**
 * Shape returned by GET /api/cv-builder/resume/candidate-profile (CandidateProfileDto).
 * The sub-objects already mirror the resume model, so mapping is almost 1:1.
 */
export interface CandidateProfileExperienceDto {
  id?: string;
  jobTitle?: string;
  company?: string;
  startDate?: string;
  endDate?: string;
  current?: boolean;
  description?: string;
  achievements?: string[];
}

export interface CandidateProfileEducationDto {
  id?: string;
  degree?: string;
  school?: string;
  startDate?: string;
  endDate?: string;
  description?: string;
}

export interface CandidateProfileCertificationDto {
  id?: string;
  name?: string;
  issuer?: string;
  date?: string;
}

export interface CandidateProfileSkillDto {
  id?: string;
  name?: string;
  level?: number;
}

export interface CandidateProfileLanguageDto {
  id?: string;
  name?: string;
  level?: string;
}

export interface CandidateProfileDto {
  id?: string;
  userId?: string;
  fullName?: string;
  email?: string;
  location?: string;
  yearsOfExperience?: number;
  skills?: CandidateProfileSkillDto[];
  experiences?: CandidateProfileExperienceDto[];
  preferredRoles?: string[];
  languages?: CandidateProfileLanguageDto[];
  education?: CandidateProfileEducationDto[];
  certifications?: CandidateProfileCertificationDto[];
  rawMarkdown?: string;
  cvFilename?: string;
}

/** Builds a fresh {@link Resume} from the user's extracted candidate profile. */
export function candidateProfileToResume(dto: CandidateProfileDto): Resume {
  const base = createEmptyResume();
  const parts = (dto.fullName ?? '').trim().split(/\s+/).filter(Boolean);
  const firstName = parts.shift() ?? '';
  const lastName = parts.join(' ');

  return {
    ...base,
    title: dto.fullName ? `CV — ${dto.fullName}` : base.title,
    personal: {
      ...base.personal,
      firstName,
      lastName,
      title: dto.preferredRoles?.[0] ?? '',
      email: dto.email ?? '',
      location: dto.location ?? '',
    },
    experiences: (dto.experiences ?? []).map(
      (e): Experience => ({
        id: e.id || uid(),
        jobTitle: e.jobTitle ?? '',
        company: e.company ?? '',
        startDate: e.startDate ?? '',
        endDate: e.endDate ?? '',
        current: e.current ?? false,
        description: e.description ?? '',
        achievements: e.achievements ?? [],
      }),
    ),
    education: (dto.education ?? []).map(
      (ed): Education => ({
        id: ed.id || uid(),
        degree: ed.degree ?? '',
        school: ed.school ?? '',
        startDate: ed.startDate ?? '',
        endDate: ed.endDate ?? '',
        description: ed.description ?? '',
      }),
    ),
    certifications: (dto.certifications ?? []).map(
      (c): Certification => ({
        id: c.id || uid(),
        name: c.name ?? '',
        issuer: c.issuer ?? '',
        date: c.date ?? '',
      }),
    ),
    skills: (dto.skills ?? []).map(
      (s): Skill => ({
        id: s.id || uid(),
        name: s.name ?? '',
        level: s.level ?? 3,
      }),
    ),
    languages: (dto.languages ?? []).map(
      (l): Language => ({
        id: l.id || uid(),
        name: l.name ?? '',
        level: l.level ?? '',
      }),
    ),
  };
}
