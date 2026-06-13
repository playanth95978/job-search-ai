package nc.opt.aiassistant.service.vector;

import java.util.List;
import org.springframework.ai.document.Document;

public interface VectorStoreServiceInterface {
    String getContext(String query);
    DataType getDataType();
    String getPrompt();
    List<Document> fetchDocuments(String query, int topK);
    List<Document> searchBM25(String query, int topK);

    int getLimit();
    void save(List<Document> docs);
}
