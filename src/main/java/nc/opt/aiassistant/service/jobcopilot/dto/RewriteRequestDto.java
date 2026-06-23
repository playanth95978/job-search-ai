package nc.opt.aiassistant.service.jobcopilot.dto;

/**
 * Request to rewrite a free-text CV field with AI.
 *
 * @param text     the original text to improve
 * @param kind     what the text is: {@code summary}, {@code experience} or {@code project}
 * @param jobTitle optional context (e.g. the related job title) to tailor the rewrite
 * @param company  optional context (e.g. the related company)
 * @param language optional language for the rewrite
 */
public record RewriteRequestDto(String text, String kind, String jobTitle, String company, String language) {}
