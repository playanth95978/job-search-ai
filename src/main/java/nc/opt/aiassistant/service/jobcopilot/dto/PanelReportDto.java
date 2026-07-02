package nc.opt.aiassistant.service.jobcopilot.dto;

import java.util.List;

/**
 * The cross deliberation delivered when the panel interview ends: each juror's verdict plus an
 * aggregated synthesis and overall score.
 */
public record PanelReportDto(String conversationId, List<JurorReport> jurors, int overallScore, String recommendation, String synthesis) {
    /** One juror's verdict, enriched with its display label. */
    public record JurorReport(
        String role,
        String speaker,
        int score,
        List<String> strengths,
        List<String> concerns,
        String recommendation
    ) {}
}
