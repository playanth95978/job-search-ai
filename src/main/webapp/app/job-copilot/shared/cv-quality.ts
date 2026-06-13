import { CandidateProfile } from './job-copilot.model';

/**
 * Heuristic CV quality metrics shown after an upload / on the profile.
 * Deterministic, explainable scoring without ML.
 */

export type CvRating = 'good' | 'fair' | 'poor';

export interface CvMetric {
  key: 'length' | 'content' | 'ats' | 'completeness';
  label: string;
  rating: CvRating;
  detail: string;
}

const RATING_LABEL: Record<CvRating, string> = {
  good: 'Good',
  fair: 'Fair',
  poor: 'Poor',
};

export function ratingLabel(rating: CvRating): string {
  return RATING_LABEL[rating];
}

const RATING_SCORE: Record<CvRating, number> = {
  good: 100,
  fair: 50,
  poor: 0,
};

const METRIC_WEIGHT: Record<CvMetric['key'], number> = {
  length: 15,
  content: 40,
  ats: 25,
  completeness: 20,
};

const PHONE_REGEX = /(?:\+\d{1,3}[\s.-]?)?(?:\(?\d+\)?[\s.-]?){8,15}/;

const ATS_SECTION_PATTERNS = [
  /exp[ée]rience/,
  /exp[ée]rience professionnelle/,
  /employment/,
  /work history/,
  /formation/,
  /formations/,
  /education/,
  /[ée]tudes/,
  /dipl[ôo]me/,
  /skills/,
  /comp[ée]tences/,
  /technologies/,
  /technical skills/,
];

/**
 * Weighted overall score (0..100).
 */
export function overallScore(metrics: CvMetric[]): number {
  if (!metrics.length) return 0;

  const totalWeight = metrics.reduce((sum, metric) => sum + METRIC_WEIGHT[metric.key], 0);

  const weightedScore = metrics.reduce((sum, metric) => sum + RATING_SCORE[metric.rating] * METRIC_WEIGHT[metric.key], 0);

  return Math.round(weightedScore / totalWeight);
}

/**
 * Maps overall score to color coding.
 */
export function overallRating(score: number): CvRating {
  if (score >= 80) return 'good';
  if (score >= 50) return 'fair';
  return 'poor';
}

/**
 * Maps a ratio (0..1) to a rating.
 */
function rate(ratio: number, goodAt: number, fairAt: number): CvRating {
  if (ratio >= goodAt) return 'good';
  if (ratio >= fairAt) return 'fair';
  return 'poor';
}

/**
 * Parses a JSON array field tolerantly.
 */
function parseArray(json?: string): unknown[] {
  if (!json) return [];

  try {
    const value: unknown = JSON.parse(json);
    return Array.isArray(value) ? value : [];
  } catch {
    return json
      .split(',')
      .map(s => s.trim())
      .filter(Boolean);
  }
}

/**
 * Removes duplicates and normalizes skills.
 */
function uniqueSkills(skills: unknown[]): string[] {
  return [...new Set(skills.map(skill => String(skill).trim().toLowerCase()).filter(Boolean))];
}

/**
 * Treats "", "[]", "\"\"", "null" as empty.
 */
function hasMeaningfulText(value?: string): boolean {
  if (!value) return false;

  const cleaned = value.replace(/["[\]{}\s]/g, '');

  return cleaned.length > 0 && cleaned.toLowerCase() !== 'null';
}

/**
 * CV length analysis.
 */
function analyzeLength(words: number): CvMetric {
  let rating: CvRating;
  let detail: string;

  if (words === 0) {
    rating = 'poor';
    detail = 'No readable text was extracted from the document.';
  } else if (words < 250) {
    rating = 'poor';
    detail = `Too short (${words} words) — aim for at least ~300 words.`;
  } else if (words <= 1000) {
    rating = 'good';
    detail = `${words} words — ideal length (1–2 pages).`;
  } else if (words <= 1400) {
    rating = 'fair';
    detail = `A bit long (${words} words) — consider trimming to 2 pages.`;
  } else {
    rating = 'poor';
    detail = `Too long (${words} words) — recruiters skim, keep it concise.`;
  }

  return {
    key: 'length',
    label: 'Length',
    rating,
    detail,
  };
}

/**
 * Content quality analysis.
 */
function analyzeContent(profile: CandidateProfile, skills: string[], experiences: unknown[]): CvMetric {
  const checks = [
    hasMeaningfulText(profile.fullName),
    hasMeaningfulText(profile.email),
    skills.length >= 3,
    experiences.length >= 1,
    hasMeaningfulText(profile.education),
    (profile.yearsOfExperience ?? 0) > 0,
  ];

  const ratio = checks.filter(Boolean).length / checks.length;

  const missing: string[] = [];

  if (!checks[1]) missing.push('contact email');
  if (!checks[2]) missing.push('skills');
  if (!checks[3]) missing.push('experience');
  if (!checks[4]) missing.push('education');

  return {
    key: 'content',
    label: 'Content',
    rating: rate(ratio, 0.8, 0.5),
    detail: missing.length ? `Missing or weak: ${missing.join(', ')}.` : 'All key sections detected.',
  };
}

/**
 * ATS friendliness analysis.
 */
function analyzeAts(text: string, words: number, skills: string[], emailField?: string): CvMetric {
  const lower = text.toLowerCase();

  const hasEmail = hasMeaningfulText(emailField) || /\b[\w.+-]+@[\w-]+\.[\w.-]+\b/.test(text);

  const hasPhone = PHONE_REGEX.test(text);

  const sectionsFound = ATS_SECTION_PATTERNS.filter(pattern => pattern.test(lower)).length;

  const skillsExtracted = skills.length >= 3;

  const parseableLength = words >= 150 && words <= 1400;

  const checks = [hasEmail, hasPhone, sectionsFound >= 2, skillsExtracted, parseableLength];

  const ratio = checks.filter(Boolean).length / checks.length;

  const issues: string[] = [];

  if (!hasEmail) issues.push('no email found');
  if (!hasPhone) issues.push('no phone found');
  if (sectionsFound < 2) issues.push('standard section headings unclear');
  if (!skillsExtracted) issues.push('few recognizable skills');

  return {
    key: 'ats',
    label: 'ATS-friendly',
    rating: rate(ratio, 0.8, 0.5),
    detail: issues.length ? `Parsers may struggle: ${issues.join(', ')}.` : 'Clear structure, easy for ATS to parse.',
  };
}

/**
 * Profile completeness analysis.
 */
function analyzeCompleteness(profile: CandidateProfile, text: string, skills: string[], experiences: unknown[]): CvMetric {
  const checks = [
    hasMeaningfulText(profile.fullName),
    hasMeaningfulText(profile.email),
    hasMeaningfulText(profile.education),
    experiences.length > 0,
    skills.length >= 3,
    /linkedin\.com/i.test(text),
  ];

  const completed = checks.filter(Boolean).length;

  const ratio = completed / checks.length;

  return {
    key: 'completeness',
    label: 'Profile completeness',
    rating: rate(ratio, 0.85, 0.5),
    detail: `${completed}/${checks.length} key profile sections detected.`,
  };
}

/**
 * Computes all CV quality metrics.
 */
export function analyzeCv(profile: CandidateProfile): CvMetric[] {
  const text = profile.rawMarkdown ?? '';

  const words = text.trim() ? text.trim().split(/\s+/).length : 0;

  const skills = uniqueSkills(parseArray(profile.skills));

  const experiences = parseArray(profile.experiences);

  return [
    analyzeLength(words),
    analyzeContent(profile, skills, experiences),
    analyzeAts(text, words, skills, profile.email),
    analyzeCompleteness(profile, text, skills, experiences),
  ];
}
