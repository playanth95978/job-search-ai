/**
 * Structured, extensible JSON model shared by every CV template and the editor.
 * Kept framework-agnostic so it can be persisted as-is (backend `resume.data` jsonb).
 */

export type ResumeTemplateId = 'modern' | 'professional' | 'minimalist' | 'australian';

export type SectionKey = 'summary' | 'experiences' | 'education' | 'skills' | 'languages' | 'projects' | 'certifications';

export const ALL_SECTIONS: SectionKey[] = ['summary', 'experiences', 'education', 'skills', 'languages', 'projects', 'certifications'];

export interface PersonalInfo {
  firstName: string;
  lastName: string;
  title: string;
  email: string;
  phone: string;
  location: string;
  linkedin: string;
  github: string;
  website: string;
  /** Right-to-work / residency status (e.g. "Australian Citizen", "Full working rights"). Expected on Australian resumes. */
  workingRights: string;
}

export interface Experience {
  id: string;
  jobTitle: string;
  company: string;
  startDate: string;
  endDate: string;
  current: boolean;
  description: string;
  achievements: string[];
}

export interface Education {
  id: string;
  degree: string;
  school: string;
  startDate: string;
  endDate: string;
  description: string;
}

export interface Project {
  id: string;
  name: string;
  description: string;
  url: string;
  technologies: string[];
}

export interface Certification {
  id: string;
  name: string;
  issuer: string;
  date: string;
}

/** Skill level on a 1..5 scale (0 = unspecified). */
export interface Skill {
  id: string;
  name: string;
  level: number;
}

export interface Language {
  id: string;
  name: string;
  level: string;
}

/** A professional referee, commonly listed on Australian/UK resumes. */
export interface Referee {
  id: string;
  name: string;
  position: string;
  company: string;
  email: string;
  phone: string;
}

export interface ResumeTheme {
  primaryColor: string;
  secondaryColor: string;
  fontFamily: string;
  fontSize: number;
}

export interface Resume {
  id?: string;
  title: string;
  personal: PersonalInfo;
  summary: string;
  experiences: Experience[];
  education: Education[];
  projects: Project[];
  certifications: Certification[];
  skills: Skill[];
  languages: Language[];
  referees: Referee[];
  template: ResumeTemplateId;
  theme: ResumeTheme;
  /** Display order of the body sections in the preview. */
  sectionOrder: SectionKey[];
  /** Sections the user chose to hide (still kept in the model). */
  hiddenSections: SectionKey[];
  updatedAt?: string;
}

export const FONT_FAMILIES = [
  { value: "'Inter', sans-serif", label: 'Inter' },
  { value: "'Roboto', sans-serif", label: 'Roboto' },
  { value: "Georgia, 'Times New Roman', serif", label: 'Georgia' },
  { value: "'Courier New', monospace", label: 'Courier' },
];

export const DEFAULT_THEME: ResumeTheme = {
  primaryColor: '#4f46e5',
  secondaryColor: '#4338ca',
  fontFamily: "'Inter', sans-serif",
  fontSize: 14,
};

/** Cheap unique id (sufficient for client-side list tracking). */
export function uid(): string {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 8);
}

export function createEmptyResume(): Resume {
  return {
    title: 'Mon CV',
    personal: {
      firstName: '',
      lastName: '',
      title: '',
      email: '',
      phone: '',
      location: '',
      linkedin: '',
      github: '',
      website: '',
      workingRights: '',
    },
    summary: '',
    experiences: [],
    education: [],
    projects: [],
    certifications: [],
    skills: [],
    languages: [],
    referees: [],
    template: 'modern',
    theme: { ...DEFAULT_THEME },
    sectionOrder: [...ALL_SECTIONS],
    hiddenSections: [],
  };
}

export function emptyExperience(): Experience {
  return { id: uid(), jobTitle: '', company: '', startDate: '', endDate: '', current: false, description: '', achievements: [] };
}

export function emptyEducation(): Education {
  return { id: uid(), degree: '', school: '', startDate: '', endDate: '', description: '' };
}

export function emptyProject(): Project {
  return { id: uid(), name: '', description: '', url: '', technologies: [] };
}

export function emptyCertification(): Certification {
  return { id: uid(), name: '', issuer: '', date: '' };
}

export function emptySkill(): Skill {
  return { id: uid(), name: '', level: 3 };
}

export function emptyLanguage(): Language {
  return { id: uid(), name: '', level: '' };
}

export function emptyReferee(): Referee {
  return { id: uid(), name: '', position: '', company: '', email: '', phone: '' };
}

/** Readable plain-text rendering of the resume, sent to the AI for review. */
type SectionBuilder = (resume: Resume) => string[];

function buildHeader(resume: Resume): string[] {
  const { personal: p } = resume;

  const lines = [`${p.firstName} ${p.lastName}${p.title ? ` — ${p.title}` : ''}`.trim()];

  const contact = [p.email, p.phone, p.location, p.linkedin, p.github, p.website].filter(Boolean).join(' | ');

  if (contact) {
    lines.push(contact);
  }

  if (p.workingRights) {
    lines.push(`Working rights: ${p.workingRights}`);
  }

  return lines;
}

function buildSummary(resume: Resume): string[] {
  return resume.summary ? ['SUMMARY', resume.summary] : [];
}

function buildExperiences(resume: Resume): string[] {
  if (!resume.experiences.length) {
    return [];
  }

  return [
    'EXPERIENCE',
    ...resume.experiences.flatMap(exp => [
      `- ${exp.jobTitle} @ ${exp.company} (${exp.startDate} - ${exp.current ? 'present' : exp.endDate})`,
      ...(exp.description ? [`  ${exp.description}`] : []),
      ...exp.achievements.map(a => `  • ${a}`),
    ]),
  ];
}

function buildEducation(resume: Resume): string[] {
  if (!resume.education.length) {
    return [];
  }

  return ['EDUCATION', ...resume.education.map(ed => `- ${ed.degree} @ ${ed.school} (${ed.startDate} - ${ed.endDate})`)];
}

function buildSkills(resume: Resume): string[] {
  const skills = resume.skills
    .map(skill => skill.name)
    .filter(Boolean)
    .join(', ');

  return skills ? ['SKILLS', skills] : [];
}

function buildLanguages(resume: Resume): string[] {
  if (!resume.languages.length) {
    return [];
  }

  return ['LANGUAGES', resume.languages.map(lang => `${lang.name} (${lang.level})`).join(', ')];
}

function buildProjects(resume: Resume): string[] {
  if (!resume.projects.length) {
    return [];
  }

  return ['PROJECTS', ...resume.projects.map(project => `- ${project.name}: ${project.description}`)];
}

function buildCertifications(resume: Resume): string[] {
  if (!resume.certifications.length) {
    return [];
  }

  return ['CERTIFICATIONS', resume.certifications.map(cert => `${cert.name} (${cert.issuer})`).join(', ')];
}

function buildReferees(resume: Resume): string[] {
  if (!resume.referees.length) {
    return [];
  }

  return ['REFEREES', ...resume.referees.map(ref => `- ${ref.name}, ${ref.position} @ ${ref.company} (${ref.email} ${ref.phone})`)];
}

const SECTION_BUILDERS: SectionBuilder[] = [
  buildSummary,
  buildExperiences,
  buildEducation,
  buildSkills,
  buildLanguages,
  buildProjects,
  buildCertifications,
  buildReferees,
];

export function resumeToPlainText(resume: Resume): string {
  const lines = buildHeader(resume);

  for (const builder of SECTION_BUILDERS) {
    const section = builder(resume);

    if (section.length) {
      lines.push('');
      lines.push(...section);
    }
  }

  return lines.join('\n');
}
