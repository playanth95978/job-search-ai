package nc.opt.aiassistant.service.jobcopilot.dto;

import java.util.List;

public record ExperienceDto(String company, String role, String startDate, String endDate, String description, List<String> technologies) {}
