package nc.opt.aiassistant.service.jobcopilot;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.domain.enumeration.EmbeddingStatus;
import nc.opt.aiassistant.repository.jobcopilot.JobOfferRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobOfferProcessingService {

    private static final int BATCH_SIZE = 50;
    private static final int MAX_RETRIES = 2;
    private final JobOfferRepository jobOfferRepository;
    private final JobOfferEmbeddingService embeddingService;

    /**
     * Background task to process pending embeddings.
     * Note: Now primarily handled by Spring Batch JobOfferBatchConfig for mass ingestion.
     */
    // @Scheduled(fixedDelay = 5000)
    public void processPendingOffers() {
        List<JobOffer> pending = jobOfferRepository.findByEmbeddingStatus(EmbeddingStatus.PENDING, PageRequest.of(0, BATCH_SIZE * 2));

        if (pending.isEmpty()) return;

        log.info("Processing {} pending job offers for embeddings", pending.size());

        // Split into batches for the embedding model
        List<List<JobOffer>> batches = IntStream.range(0, (pending.size() + BATCH_SIZE - 1) / BATCH_SIZE)
            .mapToObj(i -> pending.subList(i * BATCH_SIZE, Math.min(pending.size(), (i + 1) * BATCH_SIZE)))
            .toList();

        // Process each batch (could be parallelized with Virtual Threads)
        batches.forEach(this::processBatchAsync);
    }

    @Async("processExecutor")
    public CompletableFuture<Void> processBatchAsync(List<JobOffer> batch) {
        processBatch(batch);
        return CompletableFuture.completedFuture(null);
    }

    @Transactional
    @Async("processExecutor")
    public void processBatch(List<JobOffer> batch) {
        List<UUID> ids = batch.stream().map(JobOffer::getId).toList();

        try {
            // Mark as PROCESSING
            jobOfferRepository.updateStatus(ids, EmbeddingStatus.PROCESSING);

            List<String> texts = batch.stream().map(JobOffer::getSearchText).toList();
            List<float[]> embeddings = embeddingService.embedBatch(texts);

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
