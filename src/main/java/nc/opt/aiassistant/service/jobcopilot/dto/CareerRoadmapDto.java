package nc.opt.aiassistant.service.jobcopilot.dto;

import java.util.List;

/**
 * Full result of a career-path simulation: a deterministic skill-gap computed from real offers,
 * plus an AI-generated, offer-grounded learning roadmap toward the target role.
 *
 * @param targetRole        the role the candidate is aiming for.
 * @param horizonYears      planning horizon echoed back.
 * @param readinessScore    how ready the candidate is today (0..100), weighted by market demand.
 * @param offersAnalyzed    number of real offers the analysis is based on (transparency).
 * @param skillGaps         demanded skills, owned and missing, sorted by market demand.
 * @param intermediateRoles suggested stepping-stone roles between now and the target.
 * @param steps             ordered learning roadmap.
 * @param sampleOffers      a few real offers backing the analysis, for the user to explore.
 */
public record CareerRoadmapDto(
    String targetRole,
    int horizonYears,
    int readinessScore,
    int offersAnalyzed,
    List<SkillGapDto> skillGaps,
    List<String> intermediateRoles,
    List<RoadmapStepDto> steps,
    List<JobOfferDto> sampleOffers
) {}
