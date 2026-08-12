package nc.opt.aiassistant.service.jobcopilot.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.CandidateProfile;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.repository.jobcopilot.CandidateProfileRepository;
import nc.opt.aiassistant.service.jobcopilot.JobOfferSearchService;
import nc.opt.aiassistant.service.jobcopilot.dto.MatchResultDto;
import nc.opt.aiassistant.service.jobcopilot.dto.MatchTagDto;
import nc.opt.aiassistant.service.jobcopilot.dto.SkillDto;
import nc.opt.aiassistant.service.jobcopilot.matching.JobMatchingService;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchingTool {

    /** Offers retrieved per match run: enough for the scorer to re-rank, small enough for the context. */
    private static final int DEFAULT_CANDIDATE_POOL = 40;
    private static final int MAX_RESULTS = 15;

    private final JobMatchingService matchingService;
    private final JobOfferSearchService jobOfferSearchService;
    private final CandidateProfileRepository profileRepository;
    private final ObjectMapper objectMapper;

    @Tool(
        description = "Match the signed-in user's candidate profile against the job offers that best fit a request. " +
            "Use it for 'best offers for me', 'should I apply to X' or 'why does this offer fit my profile'. " +
            "Returns ranked matches with a score, matched skills and warnings."
    )
    public String matchJobs(
        @ToolParam(
            required = false,
            description = "What the user is looking for, in their own words (e.g. 'senior Java Angular'). Leave empty to match on the whole CV."
        ) String query,
        @ToolParam(required = false, description = "Location the user is targeting (e.g. 'Nouméa', 'Brisbane', 'Remote')") String location,
        @ToolParam(required = false, description = "How many matches to return (default 5, max 15)") Integer limit,
        ToolContext toolContext
    ) {
        String userId = ToolSupport.currentUser(toolContext);
        if (userId == null) return "You must be signed in to match jobs.";
        CandidateProfile profile = profileRepository.findTopByUserIdOrderByCreatedAtDesc(userId).orElse(null);
        if (profile == null) return "No candidate profile found. Upload a CV first.";

        // Le moteur de recherche existant (vecteur + BM25 + RRF + reranker) fournit les candidats déjà
        // ordonnés par pertinence : JobMatchingService s'appuie sur cet ordre pour la part sémantique
        // du score. On ne recharge donc jamais tout le corpus ici.
        String searchQuery = buildQuery(query, location, profile);
        List<JobOffer> offers = jobOfferSearchService.searchHybrid(searchQuery, DEFAULT_CANDIDATE_POOL);
        if (offers.isEmpty()) {
            return "No job offer matched \"%s\". Try a broader query or another location.".formatted(searchQuery);
        }

        List<MatchResultDto> results = matchingService.matchJobs(profile, offers, location);
        int max = limit == null ? 5 : Math.clamp(limit, 1, MAX_RESULTS);
        return results.stream().limit(max).map(this::formatMatch).collect(Collectors.joining("\n\n"));
    }

    /**
     * Builds the retrieval query. The user's own words win (that is what they are asking about now);
     * otherwise the profile itself becomes the query — preferred roles and skills, which is what the
     * offers' search text is embedded on.
     */
    private String buildQuery(String query, String location, CandidateProfile profile) {
        List<String> parts = new ArrayList<>();
        if (query != null && !query.isBlank()) {
            parts.add(query.trim());
        } else {
            parts.addAll(parseList(profile.getPreferredRoles()));
            parts.addAll(parseSkillNames(profile.getSkills()));
        }
        if (location != null && !location.isBlank()) {
            parts.add(location.trim());
        }
        String built = String.join(" ", parts).trim();
        // Dernier recours : un profil sans rôles ni compétences exploitables (CV mal parsé).
        return built.isBlank() ? String.valueOf(profile.getLocation()) : built;
    }

    private List<String> parseList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Skills are stored either as a plain string list (legacy) or as structured {@link SkillDto}. */
    private List<String> parseSkillNames(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            if (json.trim().startsWith("[{\"")) {
                List<SkillDto> skills = objectMapper.readValue(json, new TypeReference<List<SkillDto>>() {});
                return skills.stream().map(SkillDto::name).filter(java.util.Objects::nonNull).toList();
            }
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse candidate skills for match query");
            return List.of();
        }
    }

    private String formatMatch(MatchResultDto m) {
        StringBuilder sb = new StringBuilder();
        sb.append("**%.0f%% Match** — %s at %s\n".formatted(m.score() * 100, m.jobOffer().title(), m.jobOffer().company()));
        if (m.jobOffer().location() != null) {
            sb.append("Location: %s%s\n".formatted(m.jobOffer().location(), m.jobOffer().remote() ? " (remote)" : ""));
        }
        if (m.jobOffer().salaryMin() != null || m.jobOffer().salaryMax() != null) {
            sb.append("Salary: %s - %s\n".formatted(m.jobOffer().salaryMin(), m.jobOffer().salaryMax()));
        }
        if (m.jobOffer().applyUrl() != null) {
            sb.append("Apply: %s\n".formatted(m.jobOffer().applyUrl()));
        }
        m.strengths().forEach(s -> sb.append(formatTag(s, "+")).append("\n"));
        m.weaknesses().forEach(w -> sb.append(formatTag(w, "-")).append("\n"));
        return sb.toString();
    }

    /**
     * Renders a translatable tag into readable text for the LLM context. The frontend resolves the
     * i18n key to a localized label; here we only need it human-readable, so we humanize the key's
     * last segment (e.g. {@code locationMatch} -> {@code Location match}) and append the skill if any.
     */
    private String formatTag(MatchTagDto tag, String sign) {
        String segment = tag.key().substring(tag.key().lastIndexOf('.') + 1);
        String label = humanize(segment);
        return tag.skill() != null ? "%s %s: %s".formatted(sign, label, tag.skill()) : "%s %s".formatted(sign, label);
    }

    private static String humanize(String camelCase) {
        String spaced = camelCase.replaceAll("([A-Z])", " $1").trim();
        return spaced.isEmpty() ? spaced : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
