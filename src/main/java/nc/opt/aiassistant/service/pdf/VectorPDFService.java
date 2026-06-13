package nc.opt.aiassistant.service.pdf;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.security.SecurityUtils;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@Slf4j
public class VectorPDFService {

    private final VectorStore pdfVectorStore;

    public VectorPDFService(VectorStore pdfVectorStore) {
        this.pdfVectorStore = pdfVectorStore;
    }

    public List<Document> search(String query) {
        SearchRequest request = SearchRequest.builder()
            .query(query)
            .topK(5)
            .filterExpression("session_id == '" + SecurityUtils.getCurrentUserLogin().orElse("anonymous") + "'")
            .build();

        return pdfVectorStore.similaritySearch(request);
    }

    /**
     * Stores the given content in the PDF vector store. PgVectorStore computes the embedding and
     * persists the content along with its metadata (jsonb) used later by {@link #search(String)}.
     *
     * @param content  the content (e.g. OCR markdown) to be stored
     * @param metadata metadata to associate with the document (must include {@code session_id} for filtering)
     */
    @Transactional
    public void put(String content, Map<String, Object> metadata) {
        pdfVectorStore.add(Collections.singletonList(new Document(content, metadata)));
    }

    /**
     * Stores a batch of documents in the PDF vector store in a single call. PgVectorStore handles
     * embedding and batched inserts (see {@code maxDocumentBatchSize} in the vector store config),
     * so this is preferred over calling {@link #put(String, Map)} in a loop.
     *
     * @param documents the documents to index (each metadata must include {@code session_id} for filtering)
     */
    @Transactional
    public void put(List<Document> documents) {
        pdfVectorStore.add(documents);
    }
}
