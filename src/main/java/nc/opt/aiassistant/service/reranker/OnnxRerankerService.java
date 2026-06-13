package nc.opt.aiassistant.service.reranker;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.util.PairList;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import nc.opt.aiassistant.service.metrics.RagMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

/**
 * Service providing document reranking capabilities using an ONNX model.
 * This service uses a cross-encoder model to re-evaluate the relevance of documents
 * retrieved from an initial search against a given query.
 *
 * It uses {@link ai.onnxruntime.OrtSession} for inference and {@link ai.djl.huggingface.tokenizers.HuggingFaceTokenizer}
 * for text tokenization.
 */
@Service
public class OnnxRerankerService implements RerankerService, ScoringModel {

    private static final Logger log = LoggerFactory.getLogger(OnnxRerankerService.class);

    private final RagMetrics ragMetrics;
    private OrtEnvironment env;
    private OrtSession session;
    private HuggingFaceTokenizer tokenizer;

    public OnnxRerankerService(RagMetrics ragMetrics) {
        this.ragMetrics = ragMetrics;
    }

    private static List<Document> scoredRerankerDocuments(int topK, List<ScoredDocument> scoredDocuments) {
        List<Document> result = new ArrayList<>(topK);
        int limit = Math.min(topK, scoredDocuments.size());
        for (int i = 0; i < limit; i++) {
            Document doc = scoredDocuments.get(i).document();
            // On peut ajouter le score dans les métadonnées
            Map<String, Object> metadata = new HashMap<>(doc.getMetadata());
            metadata.put("rerankScore", scoredDocuments.get(i).score());
            result.add(new Document(doc.getId(), doc.getText(), metadata));
        }
        return result;
    }

    @Override
    public String rerankAndBuildContext(String query, List<Document> docs, int topK, int maxLength) {
        List<Document> rerankedDocs = rerank(query, docs, topK, maxLength);

        List<Document> finalDocs;

        if (rerankedDocs == null || rerankedDocs.isEmpty()) {
            finalDocs = docs.stream().limit(topK).toList(); // fallback RRF
        } else {
            finalDocs = rerankedDocs;
        }

        return finalDocs.stream().map(Document::getText).collect(Collectors.joining("\n\n"));
    }

    /**
     * Closes the ONNX session, environment, and tokenizer to release resources.
     * This method is called before the bean is destroyed by the container.
     *
     * @throws OrtException if there is an error closing the ONNX resources.
     */
    @PreDestroy
    public void close() throws OrtException {
        if (session != null) {
            session.close();
        }
        if (env != null) {
            env.close();
        }
        if (tokenizer != null) {
            tokenizer.close();
        }
    }

    /**
     * Initializes the ONNX environment, loads the model, and the tokenizer.
     * This method is automatically called after the bean's properties have been set.
     *
     * @throws Exception if there is an error loading the model or the tokenizer files.
     */
    @PostConstruct
    public void init() throws Exception {
        this.env = OrtEnvironment.getEnvironment();
        String modelPath = "scripts/onnxmodel/model.onnx";
        OrtSession.SessionOptions opts = new OrtSession.SessionOptions();

        opts.setIntraOpNumThreads(Runtime.getRuntime().availableProcessors());
        opts.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.PARALLEL);
        opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);

        this.session = env.createSession(modelPath, opts);

        this.tokenizer = HuggingFaceTokenizer.newInstance(Paths.get("scripts/onnxmodel/tokenizer.json"));
        log.info("OnnxRerankerService initialized with model: {}", modelPath);
    }

    @Override
    public Response<List<Double>> scoreAll(List<TextSegment> segments, String query) {
        if (segments == null || segments.isEmpty()) {
            return Response.from(Collections.emptyList());
        }

        PairList<String, String> batch = new PairList<>(segments.size());
        for (TextSegment segment : segments) {
            String text = segment.text();
            int idx = text.indexOf("## REFERENCES");
            if (idx != -1) {
                text = text.substring(0, idx);
            }
            batch.add(query, text);
        }

        try {
            List<Float> batchScores = calculateBatchScores(batch, RerankerService.DEFAULT_MAX_LENGTH);
            List<Double> scores = new ArrayList<>(batchScores.size());
            for (int i = 0; i < batchScores.size(); i++) {
                float score = batchScores.get(i);
                TextSegment segment = segments.get(i);
                if (segment.metadata().containsKey("type") && "abstract".equals(segment.metadata().getString("type"))) {
                    score *= 1.5f;
                }

                if (query.toLowerCase().contains("jira") && "jira".equals(segment.metadata().getString("source"))) {
                    score *= 1.5f;
                }

                scores.add((double) score);
            }
            return Response.from(scores);
        } catch (OrtException e) {
            log.error("Error during ONNX inference in ScoringModel.score: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Reranks a list of documents based on their relevance to a search query.
     * It calculates a new score for each document and returns the top K documents.
     *
     * @param query The user's search query.
     * @param documents The list of documents to rerank.
     * @param topK The maximum number of documents to return after reranking.
     * @param maxLength The token length each document is padded/truncated to for inference.
     * @return A list of reranked documents, sorted by score in descending order.
     */
    @Override
    public List<Document> rerank(String query, List<Document> documents, int topK, int maxLength) {
        if (documents == null || documents.isEmpty() || query == null || query.isBlank()) {
            return documents != null ? documents.stream().limit(topK).toList() : List.of();
        }

        List<ScoredDocument> scoredDocuments = new ArrayList<>();

        PairList<String, String> batch = new PairList<>(documents.size());

        for (Document doc : documents) {
            String text = doc.getText();

            // optimisation regex → substring
            int idx = text.indexOf("## REFERENCES");
            if (idx != -1) {
                text = text.substring(0, idx);
            }

            batch.add(query, text);
        }

        try {
            long t1 = System.currentTimeMillis();
            List<Float> batchScores = calculateBatchScores(batch, maxLength);
            for (int i = 0; i < documents.size(); i++) {
                Document doc = documents.get(i);
                float score = batchScores.get(i);
                if (doc.getMetadata().containsKey("type") && "abstract".equals(doc.getMetadata().get("type"))) {
                    score *= 1.5f;
                }

                if (query.toLowerCase().contains("jira") && "jira".equals(doc.getMetadata().get("source"))) {
                    score *= 1.5f;
                }

                scoredDocuments.add(new ScoredDocument(doc, score));
            }
            scoredDocuments = scoredDocuments.stream().sorted(Comparator.comparingDouble(ScoredDocument::score).reversed()).toList();
            long t2 = System.currentTimeMillis();
            log.info("Reranker scoring time: {}ms for {} docs ({} above threshold)", (t2 - t1), documents.size(), scoredDocuments.size());
            return scoredRerankerDocuments(topK, scoredDocuments);
        } catch (OrtException e) {
            log.error("Error during ONNX inference: {}", e.getMessage());
        }
        return List.of();
    }

    private List<Float> calculateBatchScores(PairList<String, String> batch, int maxLength) throws OrtException {
        long t1 = System.currentTimeMillis();
        Encoding[] encodings = tokenizer.batchEncode(batch);

        int batchSize = encodings.length;

        long[][] inputIds = new long[batchSize][maxLength];
        long[][] attentionMask = new long[batchSize][maxLength];

        // 🔧 build tensors batch
        for (int i = 0; i < batchSize; i++) {
            long[] ids = encodings[i].getIds();
            long[] mask = encodings[i].getAttentionMask();

            int len = Math.min(ids.length, maxLength);
            if (len > 0) {
                System.arraycopy(ids, 0, inputIds[i], 0, len);
                System.arraycopy(mask, 0, attentionMask[i], 0, len);
            }
        }

        Map<String, OnnxTensor> inputs = new HashMap<>();
        inputs.put("input_ids", OnnxTensor.createTensor(env, inputIds));
        inputs.put("attention_mask", OnnxTensor.createTensor(env, attentionMask));

        try (OrtSession.Result results = session.run(inputs)) {
            //            ragMetrics.recordOnnxInference(System.currentTimeMillis() - start);

            float[][] output = (float[][]) results.get(0).getValue();

            if (output == null || output.length == 0) {
                log.error("ONNX model returned empty output");
                return List.of();
            }

            List<Float> scores = new ArrayList<>(batchSize);

            for (int i = 0; i < batchSize; i++) {
                if (i < output.length && output[i].length > 0) {
                    scores.add(output[i][0]);
                } else {
                    log.warn("Missing output score for batch index {}", i);
                    scores.add(-10.0f); // Default low score
                }
            }
            long t2 = System.currentTimeMillis();

            log.info("Tokenization time: {}", (t2 - t1));
            return scores;
        } finally {
            for (OnnxTensor tensor : inputs.values()) {
                tensor.close();
            }
        }
    }

    /**
     * Internal record to store a document along with its calculated rerank score.
     *
     * @param document The original document.
     * @param score The calculated rerank score.
     */
    private record ScoredDocument(Document document, float score) {}
}
