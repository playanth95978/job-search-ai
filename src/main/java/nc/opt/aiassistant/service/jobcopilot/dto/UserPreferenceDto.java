package nc.opt.aiassistant.service.jobcopilot.dto;

import java.util.List;

public record UserPreferenceDto(
    Boolean remoteOnly,
    String contractType,
    Integer salaryMin,
    Integer salaryMax,
    List<String> preferredRoles,
    List<String> excludedTechnologies,
    List<String> preferredLocations
) {}
