package nc.opt.aiassistant.service.jobcopilot.dto;

import java.util.List;

/**
 * A single juror's final deliberation on the candidate.
 *
 * @param score          overall rating out of 10
 * @param strengths      what the candidate did well, from this juror's angle
 * @param concerns       reservations / red flags
 * @param recommendation STRONG_HIRE, HIRE, NEUTRAL or NO_HIRE
 */
public record JurorVerdict(int score, List<String> strengths, List<String> concerns, String recommendation) {}
