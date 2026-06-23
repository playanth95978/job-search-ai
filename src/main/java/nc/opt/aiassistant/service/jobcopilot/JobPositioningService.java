package nc.opt.aiassistant.service.jobcopilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.CandidateProfile;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.domain.OfferPositioning;
import nc.opt.aiassistant.repository.jobcopilot.CandidateProfileRepository;
import nc.opt.aiassistant.repository.jobcopilot.JobOfferRepository;
import nc.opt.aiassistant.repository.jobcopilot.OfferPositioningRepository;
import nc.opt.aiassistant.security.SecurityUtils;
import nc.opt.aiassistant.service.jobcopilot.dto.CvMatchResultDto;
import nc.opt.aiassistant.web.rest.errors.BadRequestAlertException;
import org.springframework.stereotype.Service;

/**
 * Computes (and caches) the AI "positioning" of the current user's CV against a job offer, plus a
 * "what-if" simulation. Pure orchestration over {@link CvRewriteService#matchAgainstOffer}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobPositioningService {

    private final CandidateProfileRepository profileRepository;
    private final JobOfferRepository jobOfferRepository;
    private final OfferPositioningRepository positioningRepository;
    private final JobOfferEnrichmentService jobOfferEnrichmentService;
    private final CvRewriteService cvRewriteService;
    private final ObjectMapper objectMapper;

    /** Returns the cached positioning for the offer, or computes + caches it on first request. */
    public CvMatchResultDto getPositioning(UUID offerId, boolean refresh) {
        String userId = currentUserId();

        if (!refresh) {
            var cached = positioningRepository.findByUserIdAndJobOfferId(userId, offerId);
            if (cached.isPresent()) {
                CvMatchResultDto fromCache = deserialize(cached.get().getResult());
                if (fromCache != null) {
                    return fromCache;
                }
            }
        }

        CandidateProfile profile = requireProfile(userId);
        String cvContent = buildCvContent(profile);
        String offerText = buildOfferText(offerId);

        CvMatchResultDto result = cvRewriteService.matchAgainstOffer(cvContent, offerText);
        cache(userId, offerId, result);
        return result;
    }

    /** Re-scores the CV against the offer with extra skills/experience added (not cached). */
    public CvMatchResultDto simulate(UUID offerId, List<String> additions) {
        String userId = currentUserId();
        CandidateProfile profile = requireProfile(userId);

        String cvContent = buildCvContent(profile);
        if (additions != null && !additions.isEmpty()) {
            // Explicit so the LLM treats these as ALREADY acquired (present in the resume), not as
            // skills the candidate still needs — otherwise they keep showing up as "missing".
            cvContent +=
                "\n\n=== ADDITIONAL SKILLS & EXPERIENCE (now fully part of this resume — treat as acquired, demonstrated and present) ===\n" +
                String.join(", ", additions) +
                ".";
        }
        String offerText = buildOfferText(offerId);
        return cvRewriteService.matchAgainstOffer(cvContent, offerText);
    }

    private void cache(String userId, UUID offerId, CvMatchResultDto result) {
        try {
            OfferPositioning entity = positioningRepository.findByUserIdAndJobOfferId(userId, offerId).orElseGet(OfferPositioning::new);
            entity.setUserId(userId);
            entity.setJobOfferId(offerId);
            entity.setResult(objectMapper.writeValueAsString(result));
            entity.setCreatedAt(Instant.now());
            positioningRepository.save(entity);
        } catch (Exception e) {
            log.warn("Failed to cache offer positioning for user {} / offer {}: {}", userId, offerId, e.getMessage());
        }
    }

    private CvMatchResultDto deserialize(String json) {
        try {
            return objectMapper.readValue(json, CvMatchResultDto.class);
        } catch (Exception e) {
            log.warn("Failed to read cached positioning, recomputing: {}", e.getMessage());
            return null;
        }
    }

    private String currentUserId() {
        return SecurityUtils.getCurrentUserLogin().orElseThrow(() ->
            new BadRequestAlertException("Current user login not found", "positioning", "nologin")
        );
    }

    private CandidateProfile requireProfile(String userId) {
        return profileRepository
            .findTopByUserIdOrderByCreatedAtDesc(userId)
            .orElseThrow(() -> new BadRequestAlertException("No candidate profile found", "positioning", "profileNotFound"));
    }

    /** CV text for the LLM: prefer the parsed CV markdown, else fall back to roles + skills. */
    private String buildCvContent(CandidateProfile profile) {
        if (profile.getRawMarkdown() != null && !profile.getRawMarkdown().isBlank()) {
            return profile.getRawMarkdown();
        }
        StringBuilder sb = new StringBuilder();
        if (profile.getFullName() != null) sb.append(profile.getFullName()).append('\n');
        if (profile.getPreferredRoles() != null) sb.append("Roles: ").append(profile.getPreferredRoles()).append('\n');
        if (profile.getSkills() != null) sb.append("Skills: ").append(profile.getSkills()).append('\n');
        if (profile.getExperiences() != null) sb.append("Experience: ").append(profile.getExperiences()).append('\n');
        return sb.toString().trim();
    }

    /** Ensures the offer has its full description (idempotent enrich) and builds the offer text. */
    private String buildOfferText(UUID offerId) {
        JobOffer offer = jobOfferEnrichmentService.enrich(offerId);
        if (offer == null) {
            offer = jobOfferRepository
                .findById(offerId)
                .orElseThrow(() -> new BadRequestAlertException("Offer not found", "positioning", "offerNotFound"));
        }
        StringBuilder sb = new StringBuilder();
        if (offer.getTitle() != null) sb.append(offer.getTitle()).append('\n');
        if (offer.getCompany() != null) sb.append("Company: ").append(offer.getCompany()).append('\n');
        if (offer.getLocation() != null) sb.append("Location: ").append(offer.getLocation()).append('\n');
        if (offer.getDescription() != null) sb.append('\n').append(offer.getDescription());
        return sb.toString().trim();
    }
}
