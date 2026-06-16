package nc.opt.aiassistant.service.jobcopilot;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.domain.enumeration.EmbeddingStatus;
import nc.opt.aiassistant.repository.jobcopilot.JobOfferRepository;
import nc.opt.aiassistant.service.jobcopilot.ats.AtsConnector;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lazily enriches a {@code STUB} offer (scraped from a list with only a teaser) by fetching
 * its full description from the source's detail page when the user opens it.
 *
 * <p>One HTTP request per click keeps us well under source anti-bot thresholds. After enrichment
 * the offer is flagged {@code PENDING} so the existing embedding poller indexes it within seconds —
 * no cross-thread transaction race, and consistent with the rest of the pipeline.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobOfferEnrichmentService {

    private final JobOfferRepository jobOfferRepository;
    private final JobOfferIngestionService ingestionService;
    private final List<AtsConnector> connectors;

    @Transactional
    public JobOffer enrich(UUID id) {
        JobOffer offer = jobOfferRepository.findById(id).orElseThrow();

        // Already enriched, queued or indexed: nothing to do (idempotent, no re-scrape).
        if (offer.getEmbeddingStatus() != EmbeddingStatus.STUB) {
            return offer;
        }

        JobOffer detail = fetchDetail(offer);
        if (detail != null && detail.getDescription() != null && !detail.getDescription().isBlank()) {
            offer.setDescription(detail.getDescription());
            if (detail.getSalaryMin() != null) offer.setSalaryMin(detail.getSalaryMin());
            if (detail.getSalaryMax() != null) offer.setSalaryMax(detail.getSalaryMax());
            if (isBlank(offer.getCompany())) offer.setCompany(detail.getCompany());
            if (isBlank(offer.getLocation())) offer.setLocation(detail.getLocation());
        } else {
            // Detail page unavailable (e.g. anti-bot): fall back to the teaser already on the offer.
            log.debug("Enrichment kept teaser description for offer {}", id);
        }

        offer.setSearchText(ingestionService.buildSearchText(offer));
        offer.setEmbeddingStatus(EmbeddingStatus.PENDING); // poller will embed it shortly
        offer.setUpdatedAt(Instant.now());
        return jobOfferRepository.save(offer);
    }

    private JobOffer fetchDetail(JobOffer offer) {
        if (offer.getApplyUrl() == null) return null;
        return connectors
            .stream()
            .filter(c -> c.getSource() == offer.getSource())
            .findFirst()
            .map(c -> c.fetchDetail(offer.getApplyUrl()))
            .orElse(null);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
