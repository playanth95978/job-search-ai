package nc.opt.aiassistant.service.jobcopilot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.repository.jobcopilot.OfferPositioningRepository;
import nc.opt.aiassistant.repository.jobcopilot.OfferTailoredResumeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Evicts per-offer AI caches (positioning + tailored resume) when the data they derive from changes,
 * so a new CV / profile never serves stale results computed from the previous one.
 *
 * <p>Positioning derives from the {@code CandidateProfile}; the tailored resume derives from the
 * builder {@code CvResume}. We evict broadly (per user) on any CV change to stay correct — the only
 * cost is regenerating on the next offer open.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfferCacheInvalidationService {

    private final OfferPositioningRepository positioningRepository;
    private final OfferTailoredResumeRepository tailoredRepository;

    /** Clears both caches for the user (used when the uploaded CV / candidate profile changes). */
    @Transactional
    public void evictAll(String userId) {
        if (userId == null) return;
        positioningRepository.deleteByUserId(userId);
        tailoredRepository.deleteByUserId(userId);
        log.debug("Evicted positioning + tailored caches for user {}", userId);
    }

    /** Clears only the tailored-resume cache (used when the builder resume changes). */
    @Transactional
    public void evictTailored(String userId) {
        if (userId == null) return;
        tailoredRepository.deleteByUserId(userId);
        log.debug("Evicted tailored cache for user {}", userId);
    }
}
