import { CandidateProfile } from './job-copilot.model';

/**
 * Deterministic ATS "searchability" analysis, in the spirit of Jobscan's parse report.
 *
 * Unlike the LLM simulation, every check here is explainable and reproducible: it looks at
 * the raw extracted text (what an ATS text layer would see) and the structured profile
 * (what a parser managed to pull out) and reports, field by field, what a standard ATS
 * would and would not find. No model, no score hallucination.
 */

export type AtsCheckStatus = 'pass' | 'warn' | 'fail';

export type AtsCheckCategory = 'contact' | 'sections' | 'formatting';

export interface AtsCheck {
  id: string;
  category: AtsCheckCategory;
  status: AtsCheckStatus;
  /** i18n key, jobCopilot.cvQuality.searchability.checks.<id>.label */
  labelKey: string;
  /** i18n key for the one-line explanation of the result. */
  detailKey: string;
  detailParams?: Record<string, unknown>;
  /** Extracted value shown on contact rows (email, phone…), when relevant. */
  value?: string;
}

/** What the ATS actually pulled out of the document — shown as a candidate card. */
export interface ParsedContact {
  name?: string;
  email?: string;
  phone?: string;
  linkedin?: string;
  github?: string;
  portfolio?: string;
  location?: string;
}

export interface AtsSearchability {
  /** Weighted share of checks an ATS would pass, 0..100. */
  parseRate: number;
  checks: AtsCheck[];
  passed: number;
  total: number;
  contact: ParsedContact;
}

const EMAIL_REGEX = /\b[\w.+-]+@[\w-]+\.[\w.-]+\b/;
const PHONE_REGEX = /(?:\+\d{1,3}[\s.-]?)?(?:\(?\d+\)?[\s.-]?){7,15}\d/;
const LINKEDIN_REGEX = /linkedin\.com\/(?:in|pub)\/[\w%-]+/i;
const GITHUB_REGEX = /github\.com\/[\w-]+/i;
const PORTFOLIO_REGEX = /\bhttps?:\/\/(?!(?:www\.)?(?:linkedin|github)\.com)[\w.-]+\.[a-z]{2,}(?:\/\S*)?/i;
const YEAR_REGEX = /\b(?:19|20)\d{2}\b/;

const EXPERIENCE_HEADING = /exp[ée]rienc|employment|work\s+history|parcours\s+professionnel|professional\s+experience/i;
const EDUCATION_HEADING = /formation|education|[ée]tudes|dipl[ôo]me|scolarit[ée]|academic/i;
const SKILLS_HEADING = /comp[ée]tence|\bskills\b|technologies|technical\s+skills|stack\s+technique|savoir[- ]faire/i;

/** Relative weight of each check in the overall parse rate; critical fields weigh more. */
const CHECK_WEIGHT: Record<string, number> = {
  name: 3,
  email: 3,
  phone: 2,
  links: 1,
  location: 1,
  experienceSection: 3,
  educationSection: 2,
  skillsSection: 2,
  dates: 2,
  skillsCount: 1,
};

const STATUS_SCORE: Record<AtsCheckStatus, number> = {
  pass: 1,
  warn: 0.5,
  fail: 0,
};

function hasMeaningfulText(value?: string): boolean {
  if (!value) return false;
  const cleaned = value.replace(/["[\]{}\s]/g, '');
  return cleaned.length > 0 && cleaned.toLowerCase() !== 'null';
}

function firstMatch(text: string, regex: RegExp): string | undefined {
  return text.match(regex)?.[0];
}

/**
 * True when a section heading (not just a stray body word) matching `pattern` is present.
 * Scans markdown headings, bold lines, and short all-caps lines — how ATS section splitters work.
 */
function hasSectionHeading(text: string, pattern: RegExp): boolean {
  return text.split(/\r?\n/).some(line => {
    const trimmed = line.trim();
    if (!trimmed) return false;
    const isHeading =
      /^#{1,6}\s/.test(trimmed) || // markdown heading
      /^\*\*.+\*\*$/.test(trimmed) || // bold-only line
      (trimmed.length <= 40 && trimmed === trimmed.toUpperCase() && /[A-ZÀ-Ý]/.test(trimmed)); // short all-caps
    return isHeading && pattern.test(trimmed);
  });
}

function contactCheck(id: string, value: string | undefined, foundDetail: string, missingDetail: string, warn = false): AtsCheck {
  const found = !!value;
  return {
    id,
    category: 'contact',
    status: found ? 'pass' : warn ? 'warn' : 'fail',
    labelKey: `jobCopilot.cvQuality.searchability.checks.${id}.label`,
    detailKey: `jobCopilot.cvQuality.searchability.checks.${id}.${found ? foundDetail : missingDetail}`,
    value,
  };
}

function sectionCheck(id: string, found: boolean, extractedCount: number): AtsCheck {
  // Heading present + something extracted → pass. Data extracted but no clear heading → warn
  // (ATS may have guessed). Nothing at all → fail.
  let status: AtsCheckStatus;
  if (found && extractedCount > 0) status = 'pass';
  else if (found || extractedCount > 0) status = 'warn';
  else status = 'fail';

  return {
    id,
    category: 'sections',
    status,
    labelKey: `jobCopilot.cvQuality.searchability.checks.${id}.label`,
    detailKey: `jobCopilot.cvQuality.searchability.checks.${id}.${status}`,
    detailParams: { count: extractedCount },
  };
}

/**
 * Runs the full deterministic searchability analysis over a candidate profile.
 */
export function analyzeSearchability(profile: CandidateProfile): AtsSearchability {
  const text = profile.rawMarkdown ?? '';

  const email = hasMeaningfulText(profile.email) ? profile.email : firstMatch(text, EMAIL_REGEX);
  const phone = firstMatch(text, PHONE_REGEX)?.trim();
  const linkedin = firstMatch(text, LINKEDIN_REGEX);
  const github = firstMatch(text, GITHUB_REGEX);
  const portfolio = firstMatch(text, PORTFOLIO_REGEX);
  const location = hasMeaningfulText(profile.location) ? profile.location : undefined;
  const links = linkedin ?? github ?? portfolio;

  const experiences = profile.experiences ?? [];
  const education = profile.education ?? [];
  const skills = (profile.skills ?? []).map(s => s.name ?? '').filter(Boolean);

  const contact: ParsedContact = {
    name: hasMeaningfulText(profile.fullName) ? profile.fullName : undefined,
    email,
    phone,
    linkedin,
    github,
    portfolio,
    location,
  };

  const checks: AtsCheck[] = [];

  // --- Contact information ---
  checks.push({
    id: 'name',
    category: 'contact',
    status: contact.name ? 'pass' : 'fail',
    labelKey: 'jobCopilot.cvQuality.searchability.checks.name.label',
    detailKey: `jobCopilot.cvQuality.searchability.checks.name.${contact.name ? 'found' : 'missing'}`,
    value: contact.name,
  });
  checks.push(contactCheck('email', email, 'found', 'missing'));
  checks.push(contactCheck('phone', phone, 'found', 'missing', true));
  checks.push(contactCheck('links', links, 'found', 'missing', true));
  checks.push(contactCheck('location', location, 'found', 'missing', true));

  // --- Section headings ---
  checks.push(sectionCheck('experienceSection', hasSectionHeading(text, EXPERIENCE_HEADING), experiences.length));
  checks.push(sectionCheck('educationSection', hasSectionHeading(text, EDUCATION_HEADING), education.length));
  checks.push(sectionCheck('skillsSection', hasSectionHeading(text, SKILLS_HEADING), skills.length));

  // --- Formatting / parse quality ---
  if (experiences.length > 0) {
    const datedExperiences = experiences.filter(e => YEAR_REGEX.test(`${e.startDate ?? ''} ${e.endDate ?? ''}`) || e.current).length;
    let status: AtsCheckStatus;
    if (datedExperiences === experiences.length) status = 'pass';
    else if (datedExperiences > 0) status = 'warn';
    else status = 'fail';
    checks.push({
      id: 'dates',
      category: 'formatting',
      status,
      labelKey: 'jobCopilot.cvQuality.searchability.checks.dates.label',
      detailKey: `jobCopilot.cvQuality.searchability.checks.dates.${status}`,
      detailParams: { dated: datedExperiences, total: experiences.length },
    });
  }

  const skillsStatus: AtsCheckStatus = skills.length >= 5 ? 'pass' : skills.length >= 1 ? 'warn' : 'fail';
  checks.push({
    id: 'skillsCount',
    category: 'formatting',
    status: skillsStatus,
    labelKey: 'jobCopilot.cvQuality.searchability.checks.skillsCount.label',
    detailKey: `jobCopilot.cvQuality.searchability.checks.skillsCount.${skillsStatus}`,
    detailParams: { count: skills.length },
  });

  const totalWeight = checks.reduce((sum, c) => sum + (CHECK_WEIGHT[c.id] ?? 1), 0);
  const weightedScore = checks.reduce((sum, c) => sum + STATUS_SCORE[c.status] * (CHECK_WEIGHT[c.id] ?? 1), 0);
  const parseRate = totalWeight ? Math.round((weightedScore / totalWeight) * 100) : 0;

  return {
    parseRate,
    checks,
    passed: checks.filter(c => c.status === 'pass').length,
    total: checks.length,
    contact,
  };
}

export function parseRateRating(rate: number): 'good' | 'fair' | 'poor' {
  if (rate >= 80) return 'good';
  if (rate >= 55) return 'fair';
  return 'poor';
}
