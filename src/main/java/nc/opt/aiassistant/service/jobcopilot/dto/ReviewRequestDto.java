package nc.opt.aiassistant.service.jobcopilot.dto;

/** Request to review a resume; {@code content} is a plain-text rendering of the current resume. */
public record ReviewRequestDto(String content) {}
