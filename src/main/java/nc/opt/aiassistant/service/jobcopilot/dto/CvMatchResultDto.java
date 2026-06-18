package nc.opt.aiassistant.service.jobcopilot.dto;

import java.util.List;

/**
 * Result of matching a resume against a job offer (all scores 0..100).
 */
public record CvMatchResultDto(
    int globalScore,
    int skillsScore,
    int experienceScore,
    int keywordScore,
    List<String> missingSkills,
    List<String> missingKeywords,
    List<String> strengths,
    List<String> weaknesses,
    List<String> suggestions
) {}
