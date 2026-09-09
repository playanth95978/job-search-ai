package nc.opt.aiassistant.service.jobcopilot;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.repository.jobcopilot.JobOfferRepository;
import nc.opt.aiassistant.service.chat.ReciprocalRankFusionService;
import nc.opt.aiassistant.service.reranker.RerankerService;
import nc.opt.aiassistant.service.vector.DataType;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Vector;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class JobOfferSearchService {

    /**
     * Troncature du cross-encoder pour les offres, en tokens.
     *
     * <p>Mesuré sur {@code bge-reranker-base} : passer de 256 à 128 rapporte ~1,8x, pour une
     * corrélation de rangs de ~0,83 avec le classement à 256 et un top-1 le plus souvent préservé.
     * 128 tokens couvrent titre, entreprise, contrat et le début de la description — assez pour
     * juger la pertinence d'une offre.</p>
     *
     * <p>Ne pas descendre à 64 : le gain supplémentaire est faible et le classement s'effondre
     * (top-1 nul à 60 documents).</p>
     */
    private final int rerankMaxLength;

    private final JobOfferRepository jobOfferRepository;

    @Qualifier("jobOfferEmbeddingModel")
    private final EmbeddingModel jobOfferEmbeddingModel;

    private final ReciprocalRankFusionService rrfService;
    private final RerankerService rerankerService;
    private final SearchEventLogService searchEventLogService;

    public JobOfferSearchService(
        JobOfferRepository jobOfferRepository,
        EmbeddingModel jobOfferEmbeddingModel,
        ReciprocalRankFusionService rrfService,
        RerankerService rerankerService,
        SearchEventLogService searchEventLogService,
        @Value("${reranker.job-offer-max-length:128}") int rerankMaxLength
    ) {
        this.jobOfferRepository = jobOfferRepository;
        this.jobOfferEmbeddingModel = jobOfferEmbeddingModel;
        this.rrfService = rrfService;
        this.rerankerService = rerankerService;
        this.searchEventLogService = searchEventLogService;
        this.rerankMaxLength = rerankMaxLength;
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
        return searchSemantic(query, null, topK);
    }

    /**
     * Same as {@link #searchSemantic(String, int)} restricted to a location.
     *
     * @param location optional city/region/country, matched against the offer's own columns; {@code null} or blank
     *                 disables the filter. Never concatenate it into {@code query}: see {@link #searchHybrid}.
     */
    public List<JobOffer> searchSemantic(String query, String location, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        log.info("Performing semantic (cosine) search for: '{}' (topK: {})", query.substring(0, Math.min(query.length(), 100)), topK);
        float[] queryEmbedding = jobOfferEmbeddingModel.embed(query);
        log.info("Query embedded ({} dims), querying vector index…", queryEmbedding.length);
        List<JobOffer> results = jobOfferRepository.searchVector(Vector.of(queryEmbedding), blankToNull(location), topK);
        log.info("searchVector returned {} offers", results.size());
        return results;
    }

    /** Spring Data binds a blank string as a value, not as "no filter" — normalise it once, here. */
    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    /**
     * Hybrid search for job offers using Vector and BM25, combined with RRF and Reranking.
     * High-performance implementation using parallel execution and virtual threads.
     */
    public List<JobOffer> searchHybrid(String query, int limit) {
        return searchHybrid(query, null, limit);
    }

    /**
     * Hybrid search restricted to a location.
     *
     * <p>The location is applied as a filter on both arms rather than appended to {@code query}.
     * Appended, it becomes one term among many for BM25 and is outweighed by the frequent ones: on
     * "developpeur java spring boot brisbane" the ten best lexical scores were all French offers,
     * none of them in Brisbane. Filtering instead makes both arms rank the same, correct subset.
     *
     * @param location optional city/region/country; {@code null} or blank disables the filter.
     */
    public List<JobOffer> searchHybrid(String query, String location, int limit) {
        return searchHybrid(query, location, limit, true);
    }

    /**
     * Recherche hybride dont l'étape de reranking est facultative.
     *
     * <p>Le reranker est le seul composant coûteux de la chaîne — un cross-encoder ONNX passe chaque
     * candidat avec la requête. Sur une requête d'un ou deux mots, il réordonne sans rien apprendre
     * de plus que la fusion. {@link QueryComplexity} décide, et les appelants historiques gardent le
     * comportement d'origine via la surcharge à trois arguments.
     *
     * @param rerank {@code false} pour rendre directement la fusion RRF, tronquée à {@code limit}.
     */
    public List<JobOffer> searchHybrid(String query, String location, int limit, boolean rerank) {
        log.info(
            "Performing high-performance hybrid search for: '{}' (location: {}, limit: {}, rerank: {})",
            query,
            location,
            limit,
            rerank
        );
        float[] queryEmbedding = jobOfferEmbeddingModel.embed(query);
        String locationFilter = blankToNull(location);

        // 1. Parallel execution of Vector and BM25 searches
        List<Document> vectorDocs = jobOfferRepository
            .searchVector(Vector.of(queryEmbedding), locationFilter, limit * 3)
            .stream()
            .map(this::toDocument)
            .filter(java.util.Objects::nonNull)
            .toList();

        List<Document> bm25Docs;
        try {
            bm25Docs = jobOfferRepository
                .searchBM25(query, locationFilter, limit * 3)
                .stream()
                .map(this::toDocument)
                .filter(java.util.Objects::nonNull)
                .toList();
        } catch (Exception e) {
            log.warn("BM25 search failed (ParadeDB may not be available): {}", e.getMessage());
            bm25Docs = List.of();
        }

        if (vectorDocs.isEmpty() && bm25Docs.isEmpty()) {
            return List.of();
        }

        // 2. Fusion RRF (Reciprocal Rank Fusion)
        List<Document> fusedDocs = rrfService.rrf(List.of(vectorDocs, bm25Docs), limit * 2);

        // 3. Reranking for high precision matching (Semantic Reranker) — skipped when the query is
        // simple enough that the fusion order is already the answer.
        List<Document> rankedDocs = rerank
            ? rerankerService.rerank(query, fusedDocs, limit, rerankMaxLength)
            : fusedDocs.stream().limit(limit).toList();

        // 4. Retrieve full entities while maintaining ranked order
        List<UUID> orderedIds = rankedDocs
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

        List<JobOffer> results = orderedIds.stream().map(offerMap::get).filter(Objects::nonNull).toList();

        // Journalise apres coup, avec les rangs par bras que la fusion vient d'ecraser : c'est la
        // seule occasion de les capturer, et le classement final ne les porte plus.
        searchEventLogService.record(
            query,
            locationFilter,
            rerank ? "hybrid+rerank" : "hybrid",
            "search-hybrid",
            null,
            orderedIds,
            armFeatures(vectorDocs, bm25Docs)
        );

        return results;
    }

    /**
     * Rang et score de chaque offre dans chaque bras, avant fusion.
     *
     * <p>Le score n'est pas disponible ici : {@link #toDocument} ne le transporte pas. Les rangs
     * suffisent a un modele de classement — c'est sur eux que le RRF travaille deja — et les
     * remonter demanderait de modifier la signature des requetes du repository.
     */
    private static Map<UUID, SearchEventLogService.Features> armFeatures(List<Document> vectorDocs, List<Document> bm25Docs) {
        Map<UUID, Integer> vectorRanks = ranksOf(vectorDocs);
        Map<UUID, Integer> bm25Ranks = ranksOf(bm25Docs);
        Map<UUID, SearchEventLogService.Features> features = new HashMap<>();
        Stream.concat(vectorRanks.keySet().stream(), bm25Ranks.keySet().stream())
            .distinct()
            .forEach(id -> features.put(id, new SearchEventLogService.Features(vectorRanks.get(id), bm25Ranks.get(id), null, null, null)));
        return features;
    }

    private static Map<UUID, Integer> ranksOf(List<Document> docs) {
        Map<UUID, Integer> ranks = new HashMap<>();
        for (int i = 0; i < docs.size(); i++) {
            ranks.put(UUID.fromString(docs.get(i).getId()), i + 1);
        }
        return ranks;
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
