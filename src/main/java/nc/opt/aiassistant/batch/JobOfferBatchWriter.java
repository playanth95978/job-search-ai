package nc.opt.aiassistant.batch;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.domain.enumeration.EmbeddingStatus;
import nc.opt.aiassistant.domain.enumeration.JobSource;
import nc.opt.aiassistant.repository.jobcopilot.JobOfferRepository;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

/**
 * Chunk-level upsert writer for mass ingestion.
 *
 * <p>For each chunk it resolves duplicates against the database in a single {@code IN} query per
 * source (instead of one SELECT per offer), then {@code saveAll}s the survivors in one batch:
 * <ul>
 *   <li>unchanged offers (same content hash) are skipped;</li>
 *   <li>a re-scraped STUB never overwrites an already enriched offer;</li>
 *   <li>changed offers keep the existing id and creation date and are updated.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobOfferBatchWriter implements ItemWriter<JobOffer> {

    private final JobOfferRepository jobOfferRepository;

    @Override
    public void write(Chunk<? extends JobOffer> chunk) {
        Map<JobSource, List<JobOffer>> bySource = chunk
            .getItems()
            .stream()
            .map(o -> (JobOffer) o)
            .collect(Collectors.groupingBy(JobOffer::getSource));

        List<JobOffer> toSave = new ArrayList<>();
        for (Map.Entry<JobSource, List<JobOffer>> entry : bySource.entrySet()) {
            collectToSave(entry.getKey(), entry.getValue(), toSave);
        }

        if (!toSave.isEmpty()) {
            jobOfferRepository.saveAll(toSave);
        }
        log.debug("Chunk: {} incoming -> {} saved (new/updated)", chunk.size(), toSave.size());
    }

    private void collectToSave(JobSource source, List<JobOffer> incoming, List<JobOffer> toSave) {
        Set<String> sourceIds = incoming
            .stream()
            .map(JobOffer::getSourceId)
            .filter(id -> id != null)
            .collect(Collectors.toSet());

        Map<String, JobOffer> existingBySourceId = sourceIds.isEmpty()
            ? Map.of()
            : jobOfferRepository
                  .findBySourceAndSourceIdIn(source, sourceIds)
                  .stream()
                  // a sourceId should be unique per source; keep the first if not
                  .collect(Collectors.toMap(JobOffer::getSourceId, Function.identity(), (a, b) -> a));

        Set<String> seenInChunk = new HashSet<>();
        for (JobOffer offer : incoming) {
            String sourceId = offer.getSourceId();
            // guard against duplicate sourceIds within the same chunk
            if (sourceId != null && !seenInChunk.add(sourceId)) {
                continue;
            }
            JobOffer existing = sourceId == null ? null : existingBySourceId.get(sourceId);
            if (existing != null) {
                if (offer.getContentHash() != null && offer.getContentHash().equals(existing.getContentHash())) {
                    continue; // unchanged
                }
                if (offer.getEmbeddingStatus() == EmbeddingStatus.STUB && existing.getEmbeddingStatus() != EmbeddingStatus.STUB) {
                    continue; // don't let a teaser overwrite an enriched offer
                }
                offer.setId(existing.getId());
                if (existing.getCreatedAt() != null) {
                    offer.setCreatedAt(existing.getCreatedAt());
                }
            }
            toSave.add(offer);
        }
    }
}
