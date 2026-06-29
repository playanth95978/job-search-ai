package nc.opt.aiassistant.service.jobcopilot.tools;

import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.CandidateProfile;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.repository.jobcopilot.CandidateProfileRepository;
import nc.opt.aiassistant.repository.jobcopilot.JobOfferRepository;
import nc.opt.aiassistant.service.jobcopilot.dto.MatchResultDto;
import nc.opt.aiassistant.service.jobcopilot.dto.MatchTagDto;
import nc.opt.aiassistant.service.jobcopilot.matching.JobMatchingService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchingTool {

    private final JobMatchingService matchingService;
    private final CandidateProfileRepository profileRepository;
    private final JobOfferRepository jobOfferRepository;

    @Tool(description = "Match the candidate profile against all available job offers. Returns ranked matches with scores.")
    public String matchJobs(@ToolParam(description = "User ID of the candidate") String userId) {
        CandidateProfile profile = profileRepository.findTopByUserIdOrderByCreatedAtDesc(userId).orElse(null);
        if (profile == null) return "No candidate profile found. Upload a CV first.";

        List<JobOffer> offers = jobOfferRepository.findAll();
        if (offers.isEmpty()) return "No job offers available. Search for jobs first.";

        List<MatchResultDto> results = matchingService.matchJobs(profile, offers);
        return results.stream().limit(15).map(this::formatMatch).collect(Collectors.joining("\n\n"));
    }

    private String formatMatch(MatchResultDto m) {
        StringBuilder sb = new StringBuilder();
        sb.append("**%.0f%% Match** — %s at %s\n".formatted(m.score() * 100, m.jobOffer().title(), m.jobOffer().company()));
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
