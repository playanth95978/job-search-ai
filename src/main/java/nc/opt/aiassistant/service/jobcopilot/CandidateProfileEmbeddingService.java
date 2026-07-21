package nc.opt.aiassistant.service.jobcopilot;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.CandidateProfile;
import nc.opt.aiassistant.repository.jobcopilot.CandidateProfileRepository;
import nc.opt.aiassistant.service.jobcopilot.dto.SkillDto;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Builds and persists the semantic embedding of a candidate profile.
 *
 * <p>The profile is embedded with the same model and dimensionality as job offers, so both live in
 * one vector space. This is the shared foundation (P0) for two features: the corpus-relative
 * percentile of the Application Autopsy, and the inverted offer→candidate matching of the Opportunity
 * Radar. Embedding runs asynchronously (Ollama can be slow) so it never blocks the CV upload.</p>
 */
@Slf4j
@Service
public class CandidateProfileEmbeddingService {

    private final CandidateProfileRepository profileRepository;
    private final ObjectMapper objectMapper;

    @Qualifier("jobOfferEmbeddingModel")
    private final EmbeddingModel jobOfferEmbeddingModel;

    public CandidateProfileEmbeddingService(
        CandidateProfileRepository profileRepository,
        ObjectMapper objectMapper,
        @Qualifier("jobOfferEmbeddingModel") EmbeddingModel jobOfferEmbeddingModel
    ) {
        this.profileRepository = profileRepository;
        this.objectMapper = objectMapper;
        this.jobOfferEmbeddingModel = jobOfferEmbeddingModel;
    }

    /**
     * Recomputes and stores the embedding for the given profile, off the request thread.
     * Failures are logged and swallowed: the embedding stays null and can be backfilled later.
     */
    @Async("processExecutor")
    public CompletableFuture<Void> embedAsync(UUID profileId) {
        try {
            profileRepository.findById(profileId).ifPresent(profile -> {
                String text = buildProfileText(profile);
                if (text.isBlank()) {
                    log.warn("Skipping profile embedding for {}: no roles, skills or background", profileId);
                    return;
                }
                float[] embedding = jobOfferEmbeddingModel.embed(text);
                profile.setEmbedding(embedding);
                profile.setEmbeddedAt(Instant.now());
                profileRepository.save(profile);
                log.info("Embedded candidate profile {} ({} dims)", profileId, embedding.length);
            });
        } catch (Exception e) {
            log.error("Failed to embed candidate profile {}: {}", profileId, e.getMessage());
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Canonical natural-language rendering of a profile (target roles, skills, background), shared by
     * the profile embedding and the profile→offers semantic search so both stay consistent.
     */
    public String buildProfileText(CandidateProfile profile) {
        StringBuilder sb = new StringBuilder();
        appendJsonList(sb, profile.getPreferredRoles(), "Target Roles: ");
        appendJsonList(sb, profile.getSkills(), "Skills: ");
        if (profile.getRawMarkdown() != null && !profile.getRawMarkdown().isBlank()) {
            String resume = profile.getRawMarkdown();
            sb.append("Background: ").append(resume, 0, Math.min(resume.length(), 1000));
        }
        return sb.toString().trim();
    }

    /** Skill names from the profile, handling both the structured (SkillDto) and legacy string-list formats. */
    public List<String> extractSkillNames(CandidateProfile profile) {
        String json = profile.getSkills();
        if (json == null || json.isBlank()) return List.of();
        try {
            if (json.trim().startsWith("[{\"")) {
                List<SkillDto> skillList = objectMapper.readValue(json, new TypeReference<>() {});
                return skillList
                    .stream()
                    .map(SkillDto::name)
                    .filter(s -> s != null && !s.isBlank())
                    .toList();
            }
            List<String> list = objectMapper.readValue(json, new TypeReference<>() {});
            return list
                .stream()
                .filter(s -> s != null && !s.isBlank())
                .toList();
        } catch (Exception e) {
            log.debug("Could not parse profile skills: {}", e.getMessage());
            return List.of();
        }
    }

    private void appendJsonList(StringBuilder sb, String json, String prefix) {
        if (json == null || json.isBlank()) return;
        try {
            List<String> list;
            if (json.trim().startsWith("[{\"")) {
                // Structured format (SkillDto, …): only skills are mapped to names; other objects skipped.
                if (prefix.toLowerCase().contains("skills")) {
                    List<SkillDto> skillList = objectMapper.readValue(json, new TypeReference<>() {});
                    list = skillList.stream().map(SkillDto::name).toList();
                } else {
                    list = List.of();
                }
            } else {
                // Legacy plain string list.
                list = objectMapper.readValue(json, new TypeReference<>() {});
            }
            if (!list.isEmpty()) {
                sb.append(prefix).append(String.join(", ", list)).append(". ");
            }
        } catch (Exception e) {
            log.debug("Could not parse profile list for '{}': {}", prefix.trim(), e.getMessage());
        }
    }
}
