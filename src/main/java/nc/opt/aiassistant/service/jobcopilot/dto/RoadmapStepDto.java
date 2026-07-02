package nc.opt.aiassistant.service.jobcopilot.dto;

import java.util.List;
import java.util.UUID;

/**
 * One step of the AI-generated learning roadmap toward the target role.
 *
 * @param order            1-based position in the sequence.
 * @param type             kind of step (intermediate role, certification, project or skill).
 * @param title            short label (e.g. "AWS Certified Solutions Architect – Professional").
 * @param rationale        why this step matters for the target role, grounded in the market demand.
 * @param relatedSkills    the missing skills this step helps acquire.
 * @param evidenceOfferIds ids of real offers (from the user's DB) that motivate this step.
 */
public record RoadmapStepDto(
    int order,
    StepType type,
    String title,
    String rationale,
    List<String> relatedSkills,
    List<UUID> evidenceOfferIds
) {
    public enum StepType {
        ROLE,
        CERTIFICATION,
        PROJECT,
        SKILL,
    }
}
