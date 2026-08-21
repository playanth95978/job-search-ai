import { Certification, Education, Experience, Language, Project, Resume, Skill, uid } from './resume.model';
import { GeneratedResume } from './cv-resume-api.service';

/**
 * Builds a {@link Resume} patch from an AI-generated structure. Generates fresh ids and keeps the
 * caller's current template/theme/section layout (passed via {@code current}) untouched, so a
 * generation only replaces content — not the chosen design.
 */
/** True when the current experience at this position is the same employer the AI just returned. */
function sameEmployer(currentExperience: Experience | undefined, company: string | undefined): boolean {
  const a = currentExperience?.company?.trim().toLowerCase();
  const b = company?.trim().toLowerCase();
  return !!a && a === b;
}

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
      // Working rights isn't part of the AI output: preserve whatever the user already entered.
      workingRights: current.personal.workingRights,
    },
    summary: dto.summary ?? '',
    experiences: (dto.experiences ?? []).map(
      (e, i): Experience => ({
        id: uid(),
        jobTitle: e.jobTitle ?? '',
        company: e.company ?? '',
        // The AI structure has no role location. On a translation the list is the same one in another
        // language, so the value is carried over — but only when the employer at that position still
        // matches, so a regenerated CV can never end up with a city that belongs to another job.
        location: sameEmployer(current.experiences[i], e.company) ? current.experiences[i].location : '',
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
