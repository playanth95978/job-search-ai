package nc.opt.aiassistant.service.jobcopilot.dto;

/**
 * One Server-Sent Event of a streamed assistant reply.
 *
 * @param id      stable id of the streamed message (same across its start/token/end events)
 * @param type    "start", "token", "end" or "error"
 * @param content token text for "token" events (error message for "error"); null otherwise
 */
public record ChatEvent(String id, String type, String content) {}
