package nc.opt.aiassistant.service.reranker;

import java.util.List;
import org.springframework.ai.document.Document;

/**
 * Service providing document reranking capabilities.
 */
public interface RerankerService {
    /**
     * Default token length each document is padded/truncated to for cross-encoder inference.
     * Larger values capture more context per document but increase inference cost roughly linearly.
     */
    int DEFAULT_MAX_LENGTH = 1024;

    /**
     * Reranks a list of documents based on their relevance to a search query.
     *
     * @param query     The user's search query.
     * @param documents The list of documents to rerank.
     * @param topK      The maximum number of documents to return after reranking.
     * @return A list of reranked documents, sorted by score in descending order.
     */
    default List<Document> rerank(String query, List<Document> documents, int topK) {
        return rerank(query, documents, topK, DEFAULT_MAX_LENGTH);
    }

    /**
     * Reranks a list of documents based on their relevance to a search query.
     *
     * @param query     The user's search query.
     * @param documents The list of documents to rerank.
     * @param topK      The maximum number of documents to return after reranking.
     * @param maxLength The token length each document is padded/truncated to for inference.
     *                   Tune this down for short documents (e.g. job offers) to speed up inference.
     * @return A list of reranked documents, sorted by score in descending order.
     */
    List<Document> rerank(String query, List<Document> documents, int topK, int maxLength);

    /**
     * Reranks documents and builds a context string from their content.
     *
     * @param query The user's search query.
     * @param docs  The list of documents to rerank.
     * @param topK  The maximum number of documents to include in the context.
     * @return A string containing the concatenated text of the top-ranked documents,
     *         or "NO_CONTEXT" if no relevant documents are found.
     */
    default String rerankAndBuildContext(String query, List<Document> docs, int topK) {
        return rerankAndBuildContext(query, docs, topK, DEFAULT_MAX_LENGTH);
    }

    /**
     * Reranks documents and builds a context string from their content.
     *
     * @param query     The user's search query.
     * @param docs      The list of documents to rerank.
     * @param topK      The maximum number of documents to include in the context.
     * @param maxLength The token length each document is padded/truncated to for inference.
     * @return A string containing the concatenated text of the top-ranked documents,
     *         or "NO_CONTEXT" if no relevant documents are found.
     */
    String rerankAndBuildContext(String query, List<Document> docs, int topK, int maxLength);
}
