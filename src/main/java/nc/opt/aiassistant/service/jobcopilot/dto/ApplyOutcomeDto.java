package nc.opt.aiassistant.service.jobcopilot.dto;

import nc.opt.aiassistant.domain.JobApplication;

public record ApplyOutcomeDto(boolean autoApplied, boolean autoApplySupported, String message, JobApplication application) {}
