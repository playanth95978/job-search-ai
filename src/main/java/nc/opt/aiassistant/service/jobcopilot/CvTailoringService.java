package nc.opt.aiassistant.service.jobcopilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.CvResume;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.domain.OfferTailoredResume;
import nc.opt.aiassistant.repository.jobcopilot.CvResumeRepository;
import nc.opt.aiassistant.repository.jobcopilot.JobOfferRepository;
import nc.opt.aiassistant.repository.jobcopilot.OfferTailoredResumeRepository;
import nc.opt.aiassistant.security.SecurityUtils;
import nc.opt.aiassistant.service.jobcopilot.dto.GeneratedResumeDto;
import nc.opt.aiassistant.web.rest.errors.BadRequestAlertException;
import org.springframework.stereotype.Service;

/**
 * Generates (and caches per offer) an ATS-optimized variant of the user's master resume, tailored to
 * a specific job offer. Non-destructive: the master resume is never modified here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CvTailoringService {

    private final CvResumeRepository resumeRepository;
    private final JobOfferRepository jobOfferRepository;
    private final OfferTailoredResumeRepository tailoredRepository;
    private final JobOfferEnrichmentService jobOfferEnrichmentService;
    private final CvRewriteService cvRewriteService;
    private final ObjectMapper objectMapper;

    /** Returns the cached tailored resume for the offer, or generates + caches it on first request. */
    public GeneratedResumeDto getTailored(UUID offerId, boolean refresh) {
        String userId = currentUserId();

        if (!refresh) {
            var cached = tailoredRepository.findByUserIdAndJobOfferId(userId, offerId);
            if (cached.isPresent()) {
                GeneratedResumeDto fromCache = deserialize(cached.get().getData());
                if (fromCache != null) {
                    return fromCache;
                }
            }
        }

        CvResume master = resumeRepository
            .findByUserId(userId)
            .orElseThrow(() -> new BadRequestAlertException("No resume to tailor", "tailoring", "noResume"));

        String offerText = buildOfferText(offerId);
        GeneratedResumeDto result = cvRewriteService.tailorResume(master.getData(), offerText);
        cache(userId, offerId, result);
        return result;
    }

    private void cache(String userId, UUID offerId, GeneratedResumeDto result) {
        try {
            OfferTailoredResume entity = tailoredRepository.findByUserIdAndJobOfferId(userId, offerId).orElseGet(OfferTailoredResume::new);
            entity.setUserId(userId);
            entity.setJobOfferId(offerId);
            entity.setData(objectMapper.writeValueAsString(result));
            entity.setTitle(buildTitle(result));
            entity.setCreatedAt(Instant.now());
            tailoredRepository.save(entity);
        } catch (Exception e) {
            log.warn("Failed to cache tailored resume for user {} / offer {}: {}", userId, offerId, e.getMessage());
        }
    }

    private String buildTitle(GeneratedResumeDto dto) {
        if (dto == null || dto.personal() == null) {
            return null;
        }
        String name = (dto.personal().firstName() + " " + dto.personal().lastName()).trim();
        return name.isBlank() ? null : "CV — " + name;
    }

    private GeneratedResumeDto deserialize(String json) {
        try {
            return objectMapper.readValue(json, GeneratedResumeDto.class);
        } catch (Exception e) {
            log.warn("Failed to read cached tailored resume, regenerating: {}", e.getMessage());
            return null;
        }
    }

    private String currentUserId() {
        return SecurityUtils.getCurrentUserLogin().orElseThrow(() ->
            new BadRequestAlertException("Current user login not found", "tailoring", "nologin")
        );
    }

    /** Ensures the offer has its full description (idempotent enrich) and builds the offer text. */
    private String buildOfferText(UUID offerId) {
        JobOffer offer = jobOfferEnrichmentService.enrich(offerId);
        if (offer == null) {
            offer = jobOfferRepository
                .findById(offerId)
                .orElseThrow(() -> new BadRequestAlertException("Offer not found", "tailoring", "offerNotFound"));
        }
        StringBuilder sb = new StringBuilder();
        if (offer.getTitle() != null) sb.append(offer.getTitle()).append('\n');
        if (offer.getCompany() != null) sb.append("Company: ").append(offer.getCompany()).append('\n');
        if (offer.getLocation() != null) sb.append("Location: ").append(offer.getLocation()).append('\n');
        if (offer.getDescription() != null) sb.append('\n').append(offer.getDescription());
        return sb.toString().trim();
    }
}
