package nc.opt.aiassistant.service.jobcopilot.dto;

import java.util.List;
import java.util.UUID;

public record JobOfferDto(
    UUID id,
    String title,
    String company,
    String location,
    boolean remote,
    String description,
    List<String> skills,
    String source,
    String applyUrl,
    Integer salaryMin,
    Integer salaryMax,
    String contractType
) {}
