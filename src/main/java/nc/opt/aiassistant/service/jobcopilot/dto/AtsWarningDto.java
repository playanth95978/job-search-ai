package nc.opt.aiassistant.service.jobcopilot.dto;

public record AtsWarningDto(
    String type, // e.g. "Formatting", "Content", "Dates", "Contact", "Skills"
    String level, // "CRITICAL", "WARNING", "INFO"
    String message,
    String snippet // snippet of text where the issue is found, or null
) {}
