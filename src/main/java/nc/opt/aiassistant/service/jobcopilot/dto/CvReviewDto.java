package nc.opt.aiassistant.service.jobcopilot.dto;

import java.util.List;

/**
 * Qualitative CV review produced by the LLM.
 *
 * @param score        overall quality score from 0 to 100
 * @param summary      one or two sentence overall assessment
 * @param strengths    what the CV does well
 * @param improvements concrete, actionable suggestions (e.g. "add action verbs", "quantify impact")
 */
public record CvReviewDto(int score, String summary, List<String> strengths, List<String> improvements) {}
