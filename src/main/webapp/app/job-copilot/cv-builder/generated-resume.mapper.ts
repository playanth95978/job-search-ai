import { Certification, Education, Experience, Language, Project, Resume, Skill, uid } from './resume.model';
import { GeneratedResume } from './cv-resume-api.service';

/**
 * Builds a {@link Resume} patch from an AI-generated structure. Generates fresh ids and keeps the
 * caller's current template/theme/section layout (passed via {@code current}) untouched, so a
 * generation only replaces content — not the chosen design.
 */
export function generatedResumeToResume(dto: GeneratedResume, current: Resume): Resume {
  const p = dto.personal;
  const fullName = `${p?.firstName ?? ''} ${p?.lastName ?? ''}`.trim();

  return {
    ...current,
    title: fullName ? `CV — ${fullName}` : current.title,
    personal: {
      firstName: p?.firstName ?? '',
      lastName: p?.lastName ?? '',
      title: p?.title ?? '',
      email: p?.email ?? '',
      phone: p?.phone ?? '',
      location: p?.location ?? '',
      linkedin: p?.linkedin ?? '',
      github: p?.github ?? '',
      website: p?.website ?? '',
    },
    summary: dto.summary ?? '',
    experiences: (dto.experiences ?? []).map(
      (e): Experience => ({
        id: uid(),
        jobTitle: e.jobTitle ?? '',
        company: e.company ?? '',
        startDate: e.startDate ?? '',
        endDate: e.endDate ?? '',
        current: e.current ?? false,
        description: e.description ?? '',
        achievements: (e.achievements ?? []).filter(Boolean),
      }),
    ),
    education: (dto.education ?? []).map(
      (ed): Education => ({
        id: uid(),
        degree: ed.degree ?? '',
        school: ed.school ?? '',
        startDate: ed.startDate ?? '',
        endDate: ed.endDate ?? '',
        description: ed.description ?? '',
      }),
    ),
    projects: (dto.projects ?? []).map(
      (pr): Project => ({
        id: uid(),
        name: pr.name ?? '',
        description: pr.description ?? '',
        url: pr.url ?? '',
        technologies: (pr.technologies ?? []).filter(Boolean),
      }),
    ),
    certifications: (dto.certifications ?? []).map(
      (c): Certification => ({
        id: uid(),
        name: c.name ?? '',
        issuer: c.issuer ?? '',
        date: c.date ?? '',
      }),
    ),
    skills: (dto.skills ?? []).map(
      (s): Skill => ({
        id: uid(),
        name: s.name ?? '',
        level: s.level && s.level >= 1 && s.level <= 5 ? s.level : 3,
      }),
    ),
    languages: (dto.languages ?? []).map(
      (l): Language => ({
        id: uid(),
        name: l.name ?? '',
        level: l.level ?? '',
      }),
    ),
  };
}
