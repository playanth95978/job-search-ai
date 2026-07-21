package nc.opt.aiassistant.service.jobcopilot.dto;

import java.util.List;

/**
 * Result of the Application Autopsy: what the user's own application funnel reveals about why they
 * do (or don't) get interviews, grounded in their tracked outcomes and the offers they applied to.
 *
 * @param sampleSize      number of real applications analysed (excludes DRAFT / WITHDRAWN)
 * @param enoughData      false when {@code sampleSize} is below the minimum for meaningful signal
 * @param funnel          application funnel counts and rates
 * @param score           contrast of match scores between positive- and negative-outcome applications
 * @param salary          contrast of target salaries between positive- and negative-outcome offers
 * @param skillDrivers    candidate skills over-represented in offers where the candidate progressed
 * @param recommendations short, evidence-derived, actionable statements (already localised keys or text)
 */
public record AutopsyReportDto(
    int sampleSize,
    boolean enoughData,
    Funnel funnel,
    ScoreInsight score,
    SalaryInsight salary,
    List<SkillSignal> skillDrivers,
    List<String> recommendations
) {
    public record Funnel(
        long applied,
        long interview,
        long offer,
        long rejected,
        long ghosted,
        double interviewRate,
        double offerRate,
        double ghostRate
    ) {}

    /**
     * @param avgPositive        mean match score of applications that reached interview/offer (nullable)
     * @param avgNegative        mean match score of applications rejected/ghosted (nullable)
     * @param suggestedThreshold match-score floor below which interviews rarely happen (nullable)
     */
    public record ScoreInsight(Double avgPositive, Double avgNegative, Double suggestedThreshold) {}

    /**
     * @param medianPositive median target salary (€) of positive-outcome offers (nullable)
     * @param medianNegative median target salary (€) of negative-outcome offers (nullable)
     * @param signal         true when the two medians differ enough to be worth surfacing
     */
    public record SalaryInsight(Integer medianPositive, Integer medianNegative, boolean signal) {}

    /**
     * A candidate skill and how often it appears in offers with positive vs negative outcomes.
     *
     * @param lift ratio of its positive-outcome share to its negative-outcome share (&gt;1 = favourable)
     */
    public record SkillSignal(String skill, long positiveCount, long negativeCount, double lift) {}
}
