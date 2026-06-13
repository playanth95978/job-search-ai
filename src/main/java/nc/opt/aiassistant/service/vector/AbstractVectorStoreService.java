package nc.opt.aiassistant.service.vector;

import com.openai.services.blocking.ChatService;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.service.chat.ReciprocalRankFusionService;
import nc.opt.aiassistant.service.reranker.RerankerService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

@Slf4j
public abstract class AbstractVectorStoreService implements VectorStoreServiceInterface {

    private static final Set<String> ALLOWED_TABLES = Set.of("vector_docs", "vector_code", "vector_jira");
    protected final VectorStore vectorStore;
    protected final RerankerService rerankerService;
    protected final ChatClient chatClient;
    protected final JdbcTemplate jdbcTemplate;
    protected final ReciprocalRankFusionService rrfService;

    protected AbstractVectorStoreService(
        VectorStore vectorStore,
        RerankerService rerankerService,
        ChatClient chatClient,
        JdbcTemplate jdbcTemplate,
        ReciprocalRankFusionService rrfService
    ) {
        this.vectorStore = vectorStore;
        this.rerankerService = rerankerService;
        this.chatClient = chatClient;
        this.jdbcTemplate = jdbcTemplate;
        this.rrfService = rrfService;
    }

    protected String getOrCreateConversationId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return java.util.UUID.randomUUID().toString();
        }
        return conversationId;
    }

    @Override
    @Transactional
    public void save(List<Document> docs) {
        if (null == docs || docs.isEmpty()) return;
        log.debug("Saving {} documents to vector store", docs.size());
        vectorStore.add(docs);
    }

    /**
     * Supprime tous les chunks dont le champ {@code metadata->>{key}} vaut {@code value}.
     * Utilisé pour l'<em>upsert</em> idempotent : avant de ré-indexer un document
     * (page Confluence, issue Jira, etc.) on purge ses chunks pour éviter les doublons.
     *
     * @param metadataKey clé JSON du metadata (ex. {@code "issueKey"}, {@code "pageId"})
     * @param value       valeur attendue
     * @return nombre de lignes supprimées
     */
    @Transactional
    public int deleteByMetadata(String metadataKey, String value) {
        if (metadataKey == null || metadataKey.isBlank() || value == null) return 0;
        String table = getTableName();
        if (!ALLOWED_TABLES.contains(table)) {
            throw new IllegalArgumentException("Invalid table name: " + table);
        }
        // metadataKey est contrôlé par le code appelant (constante côté service), pas une entrée user.
        if (!metadataKey.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("Invalid metadata key: " + metadataKey);
        }
        String sql = "DELETE FROM " + table + " WHERE metadata->>'" + metadataKey + "' = ?";
        int deleted = jdbcTemplate.update(sql, value);
        if (deleted > 0) {
            log.debug("Deleted {} stale row(s) from {} where {}={}", deleted, table, metadataKey, value);
        }
        return deleted;
    }

    public List<Document> rerank(String query, List<Document> docs, int topK) {
        return rerankerService.rerank(query, docs, topK);
    }

    protected String rerankAndBuildContext(String query, List<Document> docs, int topK) {
        List<Document> rerankedDocs = rerankerService.rerank(query, docs, topK);

        if (rerankedDocs.isEmpty()) return "NO_CONTEXT";

        return rerankedDocs.stream().map(Document::getText).collect(Collectors.joining("\n\n"));
    }

    public List<Document> fetchDocuments(String query, int limit) {
        List<Document> vectorDocs = vectorStore.similaritySearch(
            SearchRequest.builder().query(query).topK(limit).similarityThreshold(0.3).build()
        );
        List<Document> bm25Docs = searchBM25(query, limit);

        return rrfService.rrf(List.of(vectorDocs, bm25Docs), limit);
    }

    @Override
    public List<Document> searchBM25(String query, int limit) {
        String table = getTableName();

        if (!ALLOWED_TABLES.contains(table)) {
            throw new IllegalArgumentException("Invalid table name: " + table);
        }

        String sql =
            "SELECT id, content, metadata, paradedb.score(id) AS score " +
            "FROM " +
            table +
            " " +
            "WHERE content ||| ? " +
            "ORDER BY score DESC " + // 🔥 important
            "LIMIT ?";

        return jdbcTemplate.query(
            sql,
            (rs, rowNum) -> {
                String id = rs.getString("id");
                String content = rs.getString("content");

                return new Document(id, content, Map.of("score", rs.getDouble("score"), "source", getDataType().name()));
            },
            query,
            limit
        );
    }

    protected abstract String getTableName();

    @Override
    public int getLimit() {
        return 30;
    }
}
