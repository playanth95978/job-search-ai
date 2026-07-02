package nc.opt.aiassistant.service.jobcopilot.dto;

/**
 * Moderator routing decision: which juror should speak next and why.
 *
 * @param role   one of HR, TECH_LEAD, MANAGER
 * @param reason short justification (for logs / transparency)
 */
public record NextSpeaker(String role, String reason) {}
