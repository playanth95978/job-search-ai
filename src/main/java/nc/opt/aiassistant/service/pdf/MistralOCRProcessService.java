package nc.opt.aiassistant.service.pdf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.security.SecurityUtils;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.document.Document;
import org.springframework.ai.mistralai.ocr.MistralOcrApi;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
public class MistralOCRProcessService {

    private final VectorPDFService vectorPDFService;
    private final String mistralAiApiKey;

    public MistralOCRProcessService(
        VectorPDFService vectorPDFService,
        @Value("${spring.ai.mistralai.api-key:key}") String mistralAiApiKey,
        ChatMemoryRepository chatMemoryRepository
    ) {
        this.vectorPDFService = vectorPDFService;
        this.mistralAiApiKey = mistralAiApiKey;
    }

    // =========================
    // 🚀 ENTRY POINT (PROPRE)
    // =========================
    public OCRResult process(MultipartFile file) throws IOException {
        String docId = UUID.randomUUID().toString();
        String filename = Optional.ofNullable(file.getOriginalFilename()).orElse("document");

        // 1. OCR (synchrone pour réponse immédiate)
        MistralOcrApi.OCRResponse ocr = performOCR(file);

        // 2. Extraction texte
        String markdown = extractMarkdown(ocr);

        // 3. Lancer embedding async (non bloquant)
        CompletableFuture.runAsync(() -> {
            try {
                embedAndIndex(markdown, docId, filename);
            } catch (Exception e) {
                log.error("Embedding failed for docId={}", docId, e);
            }
        });

        // 4. Retour immédiat
        return new OCRResult(docId, filename, markdown);
    }

    // =========================
    // 🔍 OCR STEP
    // =========================
    public MistralOcrApi.OCRResponse performOCR(MultipartFile file) throws IOException {
        return performOCR(file.getBytes(), file.getOriginalFilename(), "application/pdf");
    }

    /**
     * Variante byte[] : utilisée par les pipelines d'ingestion (Confluence, Jira) pour
     * tenter un OCR Mistral en fallback quand le reader natif PDF/Tika ne sort rien
     * (typiquement pour un PDF scanné sans couche texte).
     *
     * @param bytes    contenu binaire du document (PDF / image)
     * @param filename nom logique (utilisé pour les logs et la requête Mistral)
     * @param mimeType mime type du document — par défaut {@code application/pdf}
     */
    public MistralOcrApi.OCRResponse performOCR(byte[] bytes, String filename, String mimeType) {
        MistralOcrApi client = new MistralOcrApi(mistralAiApiKey);
        String safeFilename = filename == null || filename.isBlank() ? "document" : filename;
        String safeMime = mimeType == null || mimeType.isBlank() ? "application/pdf" : mimeType;
        String base64 = Base64.getEncoder().encodeToString(bytes);

        MistralOcrApi.OCRRequest request = new MistralOcrApi.OCRRequest(
            MistralOcrApi.OCRModel.MISTRAL_OCR_LATEST.getValue(),
            safeFilename,
            new MistralOcrApi.OCRRequest.DocumentURLChunk("data:" + safeMime + ";base64," + base64),
            null,
            true,
            null,
            null
        );

        log.info("OCR start for file={} ({} bytes, mime={})", safeFilename, bytes.length, safeMime);

        return client.ocr(request).getBody();
    }

    /**
     * Helper "best-effort" : essaie l'OCR Mistral et renvoie le markdown extrait,
     * ou une chaîne vide si le service n'est pas disponible (clé API absente,
     * réseau, etc.). Conçu pour être utilisé en fallback dans les pipelines RAG.
     */
    public String ocrToMarkdown(byte[] bytes, String filename, String mimeType) {
        try {
            return extractMarkdown(performOCR(bytes, filename, mimeType));
        } catch (Exception e) {
            log.warn("Mistral OCR fallback indisponible pour {} : {}", filename, e.getMessage());
            return "";
        }
    }

    // =========================
    // 🧠 EXTRACTION MARKDOWN
    // =========================
    public String extractMarkdown(MistralOcrApi.OCRResponse response) {
        if (response == null || CollectionUtils.isEmpty(response.pages())) {
            return "";
        }

        StringBuilder fullMarkdown = new StringBuilder();

        response.pages().forEach(page -> fullMarkdown.append(page.markdown()).append("\n"));

        return fullMarkdown.toString().replaceAll("www.enhancv.com", "");
    }

    // =========================
    // 🧩 EMBEDDING + INDEX
    // =========================
    public void embedAndIndex(String markdown, String docId, String filename) {
        List<Document> documents = split(markdown);

        log.info("Embedding {} chunks for docId={}", documents.size(), docId);

        String user = SecurityUtils.getCurrentUserLogin().orElse("anonymous");

        for (Document doc : documents) {
            Map<String, Object> metadata = new HashMap<>(doc.getMetadata());
            metadata.put("session_id", user);
            metadata.put("document_id", docId);
            metadata.put("filename", filename);

            vectorPDFService.put(doc.getText(), metadata);
        }

        log.info("Document indexed docId={}", docId);
    }

    // =========================
    // ✂️ SPLIT DOCUMENT
    // =========================
    public List<Document> split(String markdown) {
        ByteArrayResource resource = new ByteArrayResource(markdown.getBytes(StandardCharsets.UTF_8));

        MarkdownDocumentReader reader = new MarkdownDocumentReader(resource, MarkdownDocumentReaderConfig.defaultConfig());

        return reader.get();
    }

    // =========================
    // 📦 DTO
    // =========================
    public record OCRResult(String docId, String filename, String rawText) {}
}
