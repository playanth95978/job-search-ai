package nc.opt.aiassistant.service.jobcopilot.dto;

/**
 * One panel turn sent to the UI.
 *
 * @param conversationId the panel session id
 * @param role           the speaking juror's role name (HR, TECH_LEAD, MANAGER)
 * @param speaker        the juror's display label (e.g. "Tech Lead")
 * @param message        the juror's reply (feedback + next question)
 */
public record PanelMessageDto(String conversationId, String role, String speaker, String message) {}
