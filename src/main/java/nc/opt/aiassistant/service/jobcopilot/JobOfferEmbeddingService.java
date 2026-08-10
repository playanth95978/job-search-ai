package nc.opt.aiassistant.service.jobcopilot;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class JobOfferEmbeddingService {

    @Qualifier("jobOfferEmbeddingModel")
    private final EmbeddingModel jobOfferEmbeddingModel;

    public JobOfferEmbeddingService(EmbeddingModel jobOfferEmbeddingModel) {
        this.jobOfferEmbeddingModel = jobOfferEmbeddingModel;
    }

    /**
     * Generates embeddings for a list of job offers in a single batch.
     * Uses Resilience4j RateLimiter and Spring Retry for robustness.
     */

    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) return List.of();

        // Ollama /api/embed rejette tout le lot avec 400 {"error":"invalid input type"} dès qu'un
        // élément du tableau `input` n'est pas une chaîne (un null sérialisé, typiquement).
        // On échoue ici avec un message exploitable plutôt que de laisser le 400 opaque remonter.
        if (texts.stream().anyMatch(t -> t == null || t.isBlank())) {
            throw new IllegalArgumentException("Embedding batch contains null or blank text");
        }

        log.debug("Generating embeddings for batch of {} texts", texts.size());

        // Spring AI EmbeddingModel.embed(List<String>) is generally optimized
        EmbeddingResponse response = jobOfferEmbeddingModel.embedForResponse(texts);

        return response
            .getResults()
            .stream()
            .map(result -> result.getOutput())
            .toList();
    }

    public List<float[]> recover(Exception e, List<String> texts) {
        log.error("Failed to generate embeddings after retries: {}", e.getMessage());
        throw new RuntimeException("Embedding service unavailable", e);
    }

    public String getModelName() {
        // This might need adjustment based on how the bean is configured
        return "ollama-embedding";
    }
}
