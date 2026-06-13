/**
 * Structured, extensible JSON model shared by every CV template and the editor.
 * Kept framework-agnostic so it can be persisted as-is (backend `resume.data` jsonb).
 */

export type ResumeTemplateId = 'modern' | 'professional' | 'minimalist';

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
  primaryColor: '#667eea',
  secondaryColor: '#764ba2',
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
    },
    summary: '',
    experiences: [],
    education: [],
    projects: [],
    certifications: [],
    skills: [],
    languages: [],
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
