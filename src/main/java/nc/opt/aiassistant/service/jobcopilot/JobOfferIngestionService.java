package nc.opt.aiassistant.service.jobcopilot;

import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.domain.enumeration.EmbeddingStatus;
import nc.opt.aiassistant.repository.jobcopilot.JobOfferRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobOfferIngestionService {

    private final JobOfferRepository jobOfferRepository;

    private static @NonNull String contentJobOfferHash(JobOffer offer) {
        String contentToHash = (offer.getTitle() + offer.getCompany() + offer.getDescription()).toLowerCase();
        return DigestUtils.md5DigestAsHex(contentToHash.getBytes());
    }

    /**
     * Ingests a list of job offers with deduplication and enrichment.
     * Fast ingestion: saves as PENDING for async processing.
     */
    @Transactional
    public void ingestAll(List<JobOffer> offers) {
        log.info("Starting mass ingestion of {} job offers", offers.size());
        List<JobOffer> toSave = offers
            .stream()
            .map(this::prepareForIngestion)
            .filter(o -> o != null)
            .toList();

        if (!toSave.isEmpty()) {
            jobOfferRepository.saveAll(toSave);
            log.info("Saved {} new/updated offers for async processing", toSave.size());
        }
    }

    /**
     * Ingests a single job offer.
     */
    @Transactional
    public void ingest(JobOffer offer) {
        JobOffer prepared = prepareForIngestion(offer);
        if (prepared != null) {
            jobOfferRepository.save(prepared);
        }
    }

    private JobOffer prepareForIngestion(JobOffer offer) {
        try {
            String hash = contentJobOfferHash(offer);

            // Check for duplicate by hash or source/sourceId
            if (offer.getSourceId() != null && offer.getSource() != null) {
                List<JobOffer> existing = jobOfferRepository.findBySourceIdAndSource(offer.getSourceId(), offer.getSource());
                if (!existing.isEmpty()) {
                    JobOffer existingOffer = existing.get(0);
                    if (hash.equals(existingOffer.getContentHash())) {
                        log.debug("Offer {}/{} already exists and is unchanged", offer.getSource(), offer.getSourceId());
                        return null; // Skip unchanged
                    }
                    // Update existing offer if changed
                    offer.setId(existingOffer.getId());
                }
            }

            log.debug("Preparing offer for ingestion: {} - {}", offer.getCompany(), offer.getTitle());

            offer.setContentHash(hash);
            offer.setSearchText(buildSearchText(offer));
            offer.setEmbeddingStatus(EmbeddingStatus.PENDING);

            Instant now = Instant.now();
            if (offer.getCreatedAt() == null) offer.setCreatedAt(now);
            offer.setUpdatedAt(now);

            return offer;
        } catch (Exception e) {
            log.error("Failed to prepare job offer {}: {}", offer.getTitle(), e.getMessage());
            return null;
        }
    }

    /**
     * Builds an optimized search text for retrieval.
     * Uses a canonical semantic structure for better retrieval performance.
     */
    private String buildSearchText(JobOffer offer) {
        StringBuilder sb = new StringBuilder();

        // Structure optimisée pour les embeddings et BM25
        sb.append("OFFRE D'EMPLOI: ").append(offer.getTitle()).append("\n");
        sb.append("ENTREPRISE: ").append(offer.getCompany()).append("\n");

        if (offer.getLocation() != null) {
            sb.append("LOCALISATION: ").append(offer.getLocation());
            if (offer.getRemote() != null && offer.getRemote()) sb.append(" (Télétravail autorisé)");
            sb.append("\n");
        }

        if (offer.getContractType() != null) sb.append("TYPE DE CONTRAT: ").append(offer.getContractType()).append("\n");
        if (offer.getExperienceLevel() != null) sb.append("NIVEAU D'EXPÉRIENCE: ").append(offer.getExperienceLevel()).append("\n");

        if (offer.getSkills() != null && !offer.getSkills().isEmpty()) {
            sb.append("COMPÉTENCES CLÉS: ").append(offer.getSkills()).append("\n");
        }

        if (offer.getSalaryMin() != null || offer.getSalaryMax() != null) {
            sb.append("RÉMUNÉRATION: ");
            if (offer.getSalaryMin() != null) sb.append(offer.getSalaryMin());
            if (offer.getSalaryMax() != null) sb.append(" - ").append(offer.getSalaryMax());
            if (offer.getSalaryCurrency() != null) sb.append(" ").append(offer.getSalaryCurrency());
            sb.append("\n");
        }

        // Nettoyage de la description
        String cleanDescription = offer
            .getDescription()
            .replaceAll("<[^>]*>", " ") // Supprime HTML
            .replaceAll("&nbsp;", " ")
            .replaceAll("\\s+", " ") // Normalise espaces
            .trim();

        // Protection contre les textes trop longs
        if (cleanDescription.length() > 2500) {
            cleanDescription = cleanDescription.substring(0, 2500) + "...";
        }

        sb.append("DESCRIPTION DU POSTE: ").append(cleanDescription);

        return sb.toString();
    }
}
