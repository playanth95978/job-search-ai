package nc.opt.aiassistant.service.jobcopilot.dto;

/**
 * Request to match a resume against a pasted job offer.
 *
 * @param content plain-text rendering of the current resume
 * @param offer   the raw job offer text pasted by the user
 */
public record CvMatchRequestDto(String content, String offer) {}
