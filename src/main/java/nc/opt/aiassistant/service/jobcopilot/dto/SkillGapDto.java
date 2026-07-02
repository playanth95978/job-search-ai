package nc.opt.aiassistant.service.jobcopilot.dto;

/**
 * A single skill demanded by the targeted senior offers, with its market demand and whether the
 * candidate already owns it. Computed deterministically from the retrieved offers — no LLM.
 *
 * @param skill       normalized skill name.
 * @param demandCount number of targeted offers that require this skill.
 * @param demandPct   share of targeted offers requiring it (0..100).
 * @param owned       true when the candidate already has this skill.
 */
public record SkillGapDto(String skill, int demandCount, int demandPct, boolean owned) {}
