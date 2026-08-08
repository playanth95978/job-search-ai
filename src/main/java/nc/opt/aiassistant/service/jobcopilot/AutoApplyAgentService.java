package nc.opt.aiassistant.service.jobcopilot;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.AutoApplyConfig;
import nc.opt.aiassistant.domain.CandidateProfile;
import nc.opt.aiassistant.domain.JobApplication;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.domain.enumeration.AutoApplyMode;
import nc.opt.aiassistant.repository.jobcopilot.AutoApplyConfigRepository;
import nc.opt.aiassistant.repository.jobcopilot.CandidateProfileRepository;
import nc.opt.aiassistant.repository.jobcopilot.JobApplicationRepository;
import nc.opt.aiassistant.service.jobcopilot.ats.JobSearchService;
import nc.opt.aiassistant.service.jobcopilot.dto.ApplyOutcomeDto;
import nc.opt.aiassistant.service.jobcopilot.dto.AutoApplyConfigDto;
import nc.opt.aiassistant.service.jobcopilot.dto.AutoApplyRunResultDto;
import nc.opt.aiassistant.service.jobcopilot.dto.AutoApplyRunResultDto.AutoApplyItemDto;
import nc.opt.aiassistant.service.jobcopilot.dto.MatchResultDto;
import nc.opt.aiassistant.service.jobcopilot.matching.JobMatchingService;
import org.springframework.stereotype.Service;

/**
 * Level-1 auto-apply agent (API-first, human-in-the-loop). For a signed-in user it: scores offers
 * against their profile (hybrid search + reranking), keeps those above the score threshold that an
 * ATS connector can actually submit to, then — per the user's {@link AutoApplyConfig} — either
 * prepares them as drafts for approval (VALIDATION) or submits them via the ATS API (AUTO), within
 * a daily cap, an optional source whitelist and a duplicate guard. It never drives a browser: it
 * only uses the structured ATS application APIs already wired in the connectors.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoApplyAgentService {

    /** How many top offers to score per run before applying the guardrails. */
    private static final int CANDIDATE_POOL = 40;

    private final AutoApplyConfigRepository configRepository;
    private final CandidateProfileRepository profileRepository;
    private final JobApplicationRepository applicationRepository;
    private final JobSearchService jobSearchService;
    private final JobMatchingService matchingService;
    private final CandidateProfileEmbeddingService profileEmbeddingService;
    private final CoverLetterService coverLetterService;
    private final ApplicationTrackingService trackingService;
    private final ObjectMapper objectMapper;

    /** Current config for the user, or an unsaved default if none exists yet. */
    public AutoApplyConfig getConfig(String userId) {
        return configRepository.findByUserId(userId).orElseGet(() -> defaultConfig(userId));
    }

    public AutoApplyConfig saveConfig(String userId, AutoApplyConfigDto dto) {
        AutoApplyConfig config = configRepository.findByUserId(userId).orElseGet(() -> defaultConfig(userId));
        config.setMode(dto.mode() != null ? AutoApplyMode.valueOf(dto.mode()) : AutoApplyMode.VALIDATION);
        config.setMinScore(dto.minScore());
        config.setMaxPerDay(dto.maxPerDay());
        config.setSources(writeJson(dto.sources()));
        return configRepository.save(config);
    }

    /** Runs the agent once for the user and returns a per-offer summary. */
    public AutoApplyRunResultDto run(String userId) {
        CandidateProfile profile = profileRepository.findTopByUserIdOrderByCreatedAtDesc(userId).orElse(null);
        if (profile == null) {
            return new AutoApplyRunResultDto(0, 0, 0, 0, "NO_PROFILE", List.of());
        }

        AutoApplyConfig config = getConfig(userId);
        double minScore = config.getMinScore() != null ? config.getMinScore() : 0.7;
        int maxPerDay = config.getMaxPerDay() != null ? config.getMaxPerDay() : 5;
        Set<String> allowedSources = parseSources(config.getSources());
        boolean auto = config.getMode() == AutoApplyMode.AUTO;

        Instant dayStart = Instant.now().truncatedTo(ChronoUnit.DAYS);
        long usedToday = applicationRepository.countByUserIdAndCreatedAtAfter(userId, dayStart);
        int budget = (int) Math.max(0, maxPerDay - usedToday);

        String query = profileEmbeddingService.buildProfileText(profile);
        List<JobOffer> offers = jobSearchService.searchHybrid(query, CANDIDATE_POOL);
        Map<UUID, JobOffer> byId = new HashMap<>();
        for (JobOffer offer : offers) {
            if (offer.getId() != null) byId.putIfAbsent(offer.getId(), offer);
        }

        List<MatchResultDto> scored = matchingService
            .matchJobs(profile, offers, null)
            .stream()
            .sorted(Comparator.comparingDouble(MatchResultDto::score).reversed())
            .toList();

        int prepared = 0;
        int submitted = 0;
        int skipped = 0;
        List<AutoApplyItemDto> items = new ArrayList<>();

        for (MatchResultDto result : scored) {
            if (prepared + submitted >= budget) break; // daily quota reached
            if (result.score() < minScore) {
                skipped++;
                continue;
            }
            UUID offerId = result.jobOffer() != null ? result.jobOffer().id() : null;
            JobOffer offer = offerId != null ? byId.get(offerId) : null;
            if (offer == null || !trackingService.canAutoApply(offer)) {
                skipped++;
                continue;
            }
            String sourceName = offer.getSource() != null ? offer.getSource().name() : null;
            if (!allowedSources.isEmpty() && (sourceName == null || !allowedSources.contains(sourceName))) {
                skipped++;
                continue;
            }
            if (applicationRepository.existsByUserIdAndJobOffer_Id(userId, offerId)) {
                skipped++;
                continue;
            }

            String coverLetter = safeCoverLetter(profile, offer);
            JobApplication app = trackingService.createApplication(userId, offer, profile, coverLetter, result.score());

            String status;
            String message = null;
            if (auto) {
                try {
                    ApplyOutcomeDto outcome = trackingService.submitApplication(app.getId(), userId);
                    if (outcome.autoApplied()) {
                        submitted++;
                        status = "SUBMITTED";
                    } else {
                        prepared++;
                        status = "PREPARED";
                        message = outcome.message();
                    }
                } catch (Exception e) {
                    prepared++;
                    status = "PREPARED";
                    message = "Submission failed: " + e.getMessage();
                    log.warn("Auto-apply submit failed for user {} offer {}: {}", userId, offerId, e.getMessage());
                }
            } else {
                prepared++;
                status = "PREPARED";
            }
            items.add(new AutoApplyItemDto(offer.getTitle(), offer.getCompany(), sourceName, result.score(), status, message));
        }

        return new AutoApplyRunResultDto(scored.size(), prepared, submitted, skipped, auto ? "AUTO" : "VALIDATION", items);
    }

    private String safeCoverLetter(CandidateProfile profile, JobOffer offer) {
        try {
            return coverLetterService.generate(profile, offer, null);
        } catch (Exception e) {
            log.warn("Cover letter generation failed for offer {}: {}", offer.getId(), e.getMessage());
            return null;
        }
    }

    private AutoApplyConfig defaultConfig(String userId) {
        AutoApplyConfig config = new AutoApplyConfig();
        config.setUserId(userId);
        config.setMode(AutoApplyMode.VALIDATION);
        config.setMinScore(0.7);
        config.setMaxPerDay(5);
        return config;
    }

    private Set<String> parseSources(String json) {
        if (json == null || json.isBlank()) return Set.of();
        try {
            return new HashSet<>(objectMapper.readValue(json, new TypeReference<List<String>>() {}));
        } catch (Exception e) {
            return Set.of();
        }
    }

    private String writeJson(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list != null ? list : List.of());
        } catch (Exception e) {
            return "[]";
        }
    }
}
