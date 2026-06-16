package nc.opt.aiassistant.service.jobcopilot.dto;

/** Three AI-generated rewrites of a CV field, in the same language as the input. */
public record RewriteResponseDto(String professional, String concise, String ats) {}
