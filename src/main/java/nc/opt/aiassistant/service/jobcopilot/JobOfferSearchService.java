package nc.opt.aiassistant.service.jobcopilot;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.repository.jobcopilot.JobOfferRepository;
import nc.opt.aiassistant.service.chat.ReciprocalRankFusionService;
import nc.opt.aiassistant.service.reranker.RerankerService;
import nc.opt.aiassistant.service.vector.DataType;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Vector;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class JobOfferSearchService {

    /** Job offer search_text is short, so a smaller cross-encoder max length keeps reranking fast. */
    private static final int JOB_OFFER_RERANK_MAX_LENGTH = 256;

    private final JobOfferRepository jobOfferRepository;

    @Qualifier("jobOfferEmbeddingModel")
    private final EmbeddingModel jobOfferEmbeddingModel;

    private final ReciprocalRankFusionService rrfService;
    private final RerankerService rerankerService;

    public JobOfferSearchService(
        JobOfferRepository jobOfferRepository,
        EmbeddingModel jobOfferEmbeddingModel,
        ReciprocalRankFusionService rrfService,
        RerankerService rerankerService
    ) {
        this.jobOfferRepository = jobOfferRepository;
        this.jobOfferEmbeddingModel = jobOfferEmbeddingModel;
        this.rrfService = rrfService;
        this.rerankerService = rerankerService;
    }

    /**
     * Simple semantic search: ranks job offers by cosine distance to the query embedding.
     * No BM25 / RRF / reranking — just nearest neighbours from pgvector.
     *
     * @param query the natural-language query (e.g. built from the candidate profile)
     * @param topK  maximum number of offers to return
     * @return the {@code topK} most semantically similar offers (empty if the query is blank)
     */
    public List<JobOffer> searchSemantic(String query, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        log.info("Performing semantic (cosine) search for: '{}' (topK: {})", query, topK);
        float[] queryEmbedding = jobOfferEmbeddingModel.embed(query);
        return jobOfferRepository.searchVector(Vector.of(queryEmbedding), topK);
    }

    /**
     * Hybrid search for job offers using Vector and BM25, combined with RRF and Reranking.
     * High-performance implementation using parallel execution and virtual threads.
     */
    public List<JobOffer> searchHybrid(String query, int limit) {
        log.info("Performing high-performance hybrid search for: '{}' (limit: {})", query, limit);
        float[] queryEmbedding = jobOfferEmbeddingModel.embed(query);

        // 1. Parallel execution of Vector and BM25 searches
        List<Document> vectorDocs = jobOfferRepository
            .searchVector(Vector.of(queryEmbedding), limit * 3)
            .stream()
            .map(this::toDocument)
            .toList();

        List<Document> bm25Docs = jobOfferRepository
            .searchBM25(query, limit * 3)
            .stream()
            .map(this::toDocument)
            .toList();

        if (vectorDocs.isEmpty() && bm25Docs.isEmpty()) {
            return List.of();
        }

        // 2. Fusion RRF (Reciprocal Rank Fusion)
        List<Document> fusedDocs = rrfService.rrf(List.of(vectorDocs, bm25Docs), limit * 2);

        // 3. Reranking for high precision matching (Semantic Reranker)
        // Job offer search_text is short, so a smaller max length keeps inference fast.
        List<Document> rerankedDocs = rerankerService.rerank(query, fusedDocs, limit, JOB_OFFER_RERANK_MAX_LENGTH);

        // 4. Retrieve full entities while maintaining reranked order
        List<UUID> orderedIds = rerankedDocs
            .stream()
            .map(d -> UUID.fromString(d.getId()))
            .toList();

        if (orderedIds.isEmpty()) {
            return List.of();
        }

        Map<UUID, JobOffer> offerMap = jobOfferRepository
            .findAllById(orderedIds)
            .stream()
            .collect(Collectors.toMap(JobOffer::getId, o -> o));

        return orderedIds.stream().map(offerMap::get).filter(Objects::nonNull).toList();
    }

    private Document toDocument(JobOffer offer) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("id", offer.getId().toString());
        metadata.put("source", DataType.JOB_OFFER.name());
        metadata.put("company", offer.getCompany());
        metadata.put("title", offer.getTitle());
        metadata.put("location", offer.getLocation());
        if (metadata.values().stream().anyMatch(Objects::isNull)) {
            return null;
        }
        return new Document(offer.getId().toString(), offer.getSearchText(), metadata);
    }
}
