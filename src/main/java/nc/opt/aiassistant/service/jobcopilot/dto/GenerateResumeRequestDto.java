package nc.opt.aiassistant.service.jobcopilot.dto;

/**
 * Request for a full AI resume generation: free-form {@code source} text (an old CV, LinkedIn
 * export, raw notes…) optionally tailored toward a target job {@code offer}.
 */
public record GenerateResumeRequestDto(String source, String offer) {}
