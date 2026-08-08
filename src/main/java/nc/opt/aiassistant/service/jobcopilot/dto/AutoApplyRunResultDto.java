package nc.opt.aiassistant.service.jobcopilot.dto;

import java.util.List;

/**
 * Outcome of one auto-apply agent run.
 *
 * @param scanned   number of scored offers considered
 * @param prepared  applications prepared as drafts (validation mode, or auto-submit that failed)
 * @param submitted applications actually submitted to an ATS (auto mode)
 * @param skipped   offers skipped (below threshold, not capable, duplicate, out of quota…)
 * @param mode      the mode that ran ({@code VALIDATION}/{@code AUTO}) or {@code NO_PROFILE}
 * @param items     per-offer detail for the run log
 */
public record AutoApplyRunResultDto(int scanned, int prepared, int submitted, int skipped, String mode, List<AutoApplyItemDto> items) {
    /** One prepared/submitted offer in a run. {@code status} is PREPARED or SUBMITTED. */
    public record AutoApplyItemDto(String title, String company, String source, double score, String status, String message) {}
}
