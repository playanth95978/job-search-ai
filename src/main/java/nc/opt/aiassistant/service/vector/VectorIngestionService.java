package nc.opt.aiassistant.service.vector;

import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VectorIngestionService {

    private final VectorStore docsVectorStore;
    private final VectorStore codeVectorStore;
    private final VectorStore pdfVectorStore;

    public VectorIngestionService(
        @Qualifier("docsVectorStore") VectorStore docsVectorStore,
        @Qualifier("codeVectorStore") VectorStore codeVectorStore,
        @Qualifier("pdfVectorStore") VectorStore pdfVectorStore
    ) {
        this.docsVectorStore = docsVectorStore;
        this.codeVectorStore = codeVectorStore;
        this.pdfVectorStore = pdfVectorStore;
    }

    @Transactional
    public void ingestDocs(List<Document> documents) {
        docsVectorStore.add(documents);
    }

    @Transactional
    public void ingestCode(List<Document> codeSnippets) {
        codeVectorStore.add(codeSnippets);
    }

    @Transactional
    public void ingestPdf(List<Document> codeSnippets) {
        pdfVectorStore.add(codeSnippets);
    }
}
