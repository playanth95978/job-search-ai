package nc.opt.aiassistant.service.jobcopilot.dto;

import java.util.List;

public record ExperienceDto(
    String id,
    String jobTitle,
    String company,
    String startDate,
    String endDate,
    Boolean current,
    String description,
    java.util.List<String> achievements
) {}
