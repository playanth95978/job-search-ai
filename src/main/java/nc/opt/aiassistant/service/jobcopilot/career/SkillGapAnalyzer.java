package nc.opt.aiassistant.service.jobcopilot.career;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import nc.opt.aiassistant.service.jobcopilot.dto.SkillGapDto;

/**
 * Pure, deterministic core of the career simulator: given the candidate's skills and the skills
 * demanded by the targeted senior offers, it computes the market-demand ranking, the skill gap and
 * a readiness score. No Spring, no LLM — fully reproducible and unit-testable.
 */
public final class SkillGapAnalyzer {

    private SkillGapAnalyzer() {}

    /** Skills demanded by one real offer, kept with its id so steps can be grounded back to it. */
    public record OfferSkills(UUID offerId, Collection<String> skills) {}

    /**
     * @param readinessScore  demand-weighted share of demanded skills the candidate already owns (0..100).
     * @param offersAnalyzed  number of offers the analysis is based on.
     * @param gaps            demanded skills (owned + missing), sorted by market demand then name.
     * @param evidenceBySkill normalized skill → ids of offers demanding it (for grounding roadmap steps).
     */
    public record Result(int readinessScore, int offersAnalyzed, List<SkillGapDto> gaps, Map<String, List<UUID>> evidenceBySkill) {}

    /** Lowercase + trim, so candidate and offer skills compare on the same footing. */
    public static String normalize(String skill) {
        return skill == null ? "" : skill.trim().toLowerCase(Locale.ROOT);
    }

    public static Result analyze(Collection<String> candidateSkills, List<OfferSkills> offers) {
        Set<String> owned = candidateSkills
            .stream()
            .map(SkillGapAnalyzer::normalize)
            .filter(s -> !s.isBlank())
            .collect(Collectors.toSet());

        int offersAnalyzed = offers.size();
        Map<String, Integer> demand = new LinkedHashMap<>(); // normalized skill → nb offers demanding it
        Map<String, String> label = new HashMap<>(); // normalized → first-seen readable label
        Map<String, List<UUID>> evidence = new HashMap<>(); // normalized → offer ids

        for (OfferSkills offer : offers) {
            Set<String> seenInOffer = new HashSet<>(); // count a skill at most once per offer
            for (String raw : offer.skills()) {
                String n = normalize(raw);
                if (n.isBlank() || !seenInOffer.add(n)) {
                    continue;
                }
                demand.merge(n, 1, Integer::sum);
                label.putIfAbsent(n, raw.trim());
                evidence.computeIfAbsent(n, k -> new ArrayList<>()).add(offer.offerId());
            }
        }

        List<SkillGapDto> gaps = demand
            .entrySet()
            .stream()
            .sorted(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed().thenComparing(Map.Entry::getKey))
            .map(e -> {
                int count = e.getValue();
                int pct = offersAnalyzed == 0 ? 0 : (int) Math.round((100.0 * count) / offersAnalyzed);
                return new SkillGapDto(label.get(e.getKey()), count, pct, owned.contains(e.getKey()));
            })
            .toList();

        // Readiness is demand-weighted: owning a skill required by 10 offers counts more than a rare one.
        int totalWeight = demand.values().stream().mapToInt(Integer::intValue).sum();
        int ownedWeight = demand
            .entrySet()
            .stream()
            .filter(e -> owned.contains(e.getKey()))
            .mapToInt(Map.Entry::getValue)
            .sum();
        int readiness = totalWeight == 0 ? 0 : (int) Math.round((100.0 * ownedWeight) / totalWeight);

        return new Result(readiness, offersAnalyzed, gaps, evidence);
    }
}
