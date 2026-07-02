package nc.opt.aiassistant.service.jobcopilot.dto;

import java.util.List;

public record AtsSimulationDto(String rawText, List<AtsWarningDto> warnings, List<String> recommendations) {}
