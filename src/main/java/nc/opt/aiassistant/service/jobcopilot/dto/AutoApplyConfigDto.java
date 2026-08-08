package nc.opt.aiassistant.service.jobcopilot.dto;

import java.util.List;

/**
 * Auto-apply agent configuration exchanged with the frontend.
 *
 * @param mode      {@code VALIDATION} or {@code AUTO}
 * @param minScore  minimum match score [0..1]
 * @param maxPerDay maximum applications created per day
 * @param sources   allowed source names (empty = every auto-apply-capable source)
 */
public record AutoApplyConfigDto(String mode, double minScore, int maxPerDay, List<String> sources) {}
