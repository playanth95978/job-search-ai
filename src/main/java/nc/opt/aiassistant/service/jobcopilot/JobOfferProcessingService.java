package nc.opt.aiassistant.service.jobcopilot;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.domain.enumeration.EmbeddingStatus;
import nc.opt.aiassistant.repository.jobcopilot.JobOfferRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobOfferProcessingService {

    /** Small batches keep a single /api/embed call fast enough for CPU-only Ollama hosts. */
    private static final int BATCH_SIZE = 16;
    /** Offers pulled per scheduling tick, processed as sequential sub-batches. */
    private static final int FETCH_SIZE = 64;
    private static final int MAX_RETRIES = 2;
    private final JobOfferRepository jobOfferRepository;
    private final JobOfferEmbeddingService embeddingService;

    /**
     * Background task to process pending embeddings.
     * Note: Now primarily handled by Spring Batch JobOfferBatchConfig for mass ingestion.
     *
     * <p>Batches are processed <b>sequentially</b>: a single CPU-bound Ollama instance cannot serve
     * concurrent embed calls without timing out, so parallel fan-out was the main cause of failures.</p>
     */
    @Scheduled(fixedDelay = 500000)
    public void processPendingOffers() {
        List<JobOffer> pending = jobOfferRepository.findByEmbeddingStatus(EmbeddingStatus.PENDING, PageRequest.of(0, FETCH_SIZE));

        if (pending.isEmpty()) return;

        log.info("Processing {} pending job offers for embeddings", pending.size());

        List<List<JobOffer>> batches = IntStream.range(0, (pending.size() + BATCH_SIZE - 1) / BATCH_SIZE)
            .mapToObj(i -> pending.subList(i * BATCH_SIZE, Math.min(pending.size(), (i + 1) * BATCH_SIZE)))
            .toList();

        // Sequential, not parallel: avoids overloading a single Ollama instance.
        batches.forEach(this::processBatch);
    }

    /**
     * Periodically re-queues offers stuck in FAILED (e.g. Ollama was temporarily down) so they get
     * another chance once the embedding backend recovers. Runs on a slow cadence to avoid churn.
     */
    @Scheduled(fixedDelay = 1800000, initialDelay = 600000)
    public void requeueFailedOffers() {
        int requeued = jobOfferRepository.requeueFailed();
        if (requeued > 0) {
            log.info("Re-queued {} offer(s) from FAILED to PENDING for another embedding attempt", requeued);
        }
    }

    @Transactional
    public void reindexAll() {
        log.info("Starting full reindexing of job offers");
        jobOfferRepository.updateAllStatus(EmbeddingStatus.PENDING);

        // Process everything in batches
        boolean hasMore = true;
        while (hasMore) {
            List<JobOffer> pending = jobOfferRepository.findByEmbeddingStatus(EmbeddingStatus.PENDING, PageRequest.of(0, BATCH_SIZE));
            if (pending.isEmpty()) {
                hasMore = false;
            } else {
                processBatch(pending);
            }
        }
        log.info("Finished full reindexing of job offers");
    }

    @Transactional
    public void processBatch(List<JobOffer> batch) {
        List<UUID> ids = batch.stream().map(JobOffer::getId).toList();

        try {
            // Mark as PROCESSING
            jobOfferRepository.updateStatus(ids, EmbeddingStatus.PROCESSING);

            List<String> texts = batch.stream().map(JobOffer::getSearchText).toList();
            List<float[]> embeddings = embeddingService.embedBatch(texts);
            log.debug("Generated embeddings for {} offers", batch.size());
            embeddings.forEach(embedding -> {
                log.debug("Embedding: {}", embedding);
            });

            for (int i = 0; i < batch.size(); i++) {
                JobOffer offer = batch.get(i);
                offer.setEmbedding(embeddings.get(i));
                offer.setEmbeddingStatus(EmbeddingStatus.COMPLETED);
                offer.setEmbeddingModel(embeddingService.getModelName());
                offer.setIndexedAt(Instant.now());
                offer.setRetryCount(0);
                offer.setIndexingError(null);
            }

            jobOfferRepository.saveAll(batch);
            log.debug("Successfully processed batch of {} offers", batch.size());
        } catch (Exception e) {
            log.error("Failed to process batch: {}", e.getMessage());
            handleBatchError(batch, e.getMessage());
        }
    }

    private void handleBatchError(List<JobOffer> batch, String error) {
        for (JobOffer offer : batch) {
            int retries = (offer.getRetryCount() == null ? 0 : offer.getRetryCount()) + 1;
            offer.setRetryCount(retries);
            offer.setIndexingError(error);

            if (retries >= MAX_RETRIES) {
                offer.setEmbeddingStatus(EmbeddingStatus.FAILED);
                log.error("Offer {} failed after {} retries", offer.getId(), MAX_RETRIES);
            } else {
                offer.setEmbeddingStatus(EmbeddingStatus.PENDING); // Retry later
            }
        }
        jobOfferRepository.saveAll(batch);
    }
}
