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
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;
import nc.opt.aiassistant.service.metrics.RagMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service providing document reranking capabilities using an ONNX model.
 * This service uses a cross-encoder model to re-evaluate the relevance of documents
 * retrieved from an initial search against a given query.
 *
 * It uses {@link ai.onnxruntime.OrtSession} for inference and {@link ai.djl.huggingface.tokenizers.HuggingFaceTokenizer}
 * for text tokenization.
 */
public class OnnxRerankerService implements RerankerService, ScoringModel {

    private static final Logger log = LoggerFactory.getLogger(OnnxRerankerService.class);

    private final RagMetrics ragMetrics;

    /**
     * Threads ONNX alloués à <b>une</b> inférence. Défaut historique : tous les cœurs — ce qui fait
     * que deux recherches simultanées se disputent la machine entière. Réglable par
     * {@code reranker.intra-op-threads} pour arbitrer latence unitaire contre débit concurrent.
     */
    @Value("${reranker.intra-op-threads:0}")
    private int intraOpThreads;

    /**
     * Inférences simultanées autorisées ; au-delà, les appelants attendent au lieu de saturer le CPU.
     * {@code 0} = illimité (comportement historique).
     */
    @Value("${reranker.max-concurrent-inferences:0}")
    private int maxConcurrentInferences;

    /**
     * Modèle chargé. Défaut : {@code bge-reranker-base}, mesuré 2,9× plus rapide que
     * {@code bge-reranker-v2-m3} pour la meilleure fidélité de classement des candidats évalués
     * (voir {@code RerankerModelComparisonBenchmark}).
     *
     * <p>Repli si le VPS reste trop lent : {@code scripts/rerankers/mminilm/model.onnx}, 7,3× plus
     * rapide, au prix d'un classement plus éloigné (Spearman ~0,58 contre ~0,75). Penser à changer
     * le tokenizer <b>en même temps</b>.</p>
     */
    @Value("${reranker.model-path:scripts/rerankers/bge-base/model.onnx}")
    private String modelPath;

    /**
     * Tokenizer du modèle. <b>Toujours solidaire de {@code model-path}</b> : chaque modèle a son
     * vocabulaire, en associer un autre produirait des identifiants de tokens sans rapport avec les
     * poids, donc des scores silencieusement absurdes — sans la moindre erreur au démarrage.
     */
    @Value("${reranker.tokenizer-path:scripts/rerankers/bge-base/tokenizer.json}")
    private String tokenizerPath;

    /** {@code null} quand la concurrence n'est pas bornée. */
    private Semaphore inferenceLimiter;

    private OrtEnvironment env;
    private OrtSession session;
    private HuggingFaceTokenizer tokenizer;

    public OnnxRerankerService(RagMetrics ragMetrics) {
        this(ragMetrics, 0, 0);
    }

    /**
     * @param intraOpThreads          threads ONNX par inférence ; {@code 0} = tous les cœurs.
     * @param maxConcurrentInferences inférences simultanées autorisées ; {@code 0} = illimité.
     */
    public OnnxRerankerService(RagMetrics ragMetrics, int intraOpThreads, int maxConcurrentInferences) {
        this(
            ragMetrics,
            intraOpThreads,
            maxConcurrentInferences,
            "scripts/rerankers/bge-base/model.onnx",
            "scripts/rerankers/bge-base/tokenizer.json"
        );
    }

    public OnnxRerankerService(
        RagMetrics ragMetrics,
        int intraOpThreads,
        int maxConcurrentInferences,
        String modelPath,
        String tokenizerPath
    ) {
        this.ragMetrics = ragMetrics;
        this.intraOpThreads = intraOpThreads;
        this.maxConcurrentInferences = maxConcurrentInferences;
        this.modelPath = modelPath;
        this.tokenizerPath = tokenizerPath;
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
        OrtSession.SessionOptions opts = new OrtSession.SessionOptions();

        int threads = intraOpThreads > 0 ? intraOpThreads : Runtime.getRuntime().availableProcessors();
        opts.setIntraOpNumThreads(threads);
        opts.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.PARALLEL);
        opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);

        // Message explicite plutôt qu'une erreur native ONNX illisible : le modèle n'est pas versionné,
        // il doit être installé au déploiement (voir scripts/download-rerankers.sh).
        requireFile(modelPath, "modèle de reranking");
        requireFile(tokenizerPath, "tokenizer de reranking");

        this.session = env.createSession(modelPath, opts);
        this.inferenceLimiter = maxConcurrentInferences > 0 ? new Semaphore(maxConcurrentInferences, true) : null;

        this.tokenizer = HuggingFaceTokenizer.newInstance(Paths.get(tokenizerPath));
        log.info(
            "OnnxRerankerService initialized with model: {} (tokenizer={}, intraOpThreads={}, maxConcurrentInferences={})",
            modelPath,
            tokenizerPath,
            threads,
            maxConcurrentInferences > 0 ? maxConcurrentInferences : "illimité"
        );
    }

    private static void requireFile(String path, String label) {
        if (!java.nio.file.Files.exists(Paths.get(path))) {
            throw new IllegalStateException(
                "Fichier introuvable pour le " +
                    label +
                    " : " +
                    Paths.get(path).toAbsolutePath() +
                    " — exécuter ./scripts/download-rerankers.sh sur cette machine."
            );
        }
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
        long preparationStart = System.currentTimeMillis();
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

        long preparationMs = System.currentTimeMillis() - preparationStart;

        // Les tenseurs d'entrée détiennent de la mémoire hors tas : sans fermeture explicite, elle
        // n'est rendue qu'au bon vouloir du GC, invisible depuis les métriques JVM.
        try (AutoCloseable ignored = () -> inputs.values().forEach(OnnxTensor::close); InferenceSlot slot = acquireInferenceSlot()) {
            long inferenceStart = System.currentTimeMillis();
            List<Float> scores = runInference(inputs, batchSize);

            // Les deux phases sont journalisées séparément, et nommées pour ce qu'elles sont : un
            // compteur unique intitulé « tokenisation » mais couvrant aussi l'inférence a déjà fait
            // chercher l'optimisation du mauvais côté. Mesuré ici, l'inférence pèse ~99 %.
            log.debug(
                "Rerank de {} documents ({} tokens) : préparation {} ms, inférence {} ms",
                batchSize,
                maxLength,
                preparationMs,
                System.currentTimeMillis() - inferenceStart
            );
            return scores;
        } catch (OrtException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("ONNX inference failed", e);
        }
    }

    /** Borne la concurrence quand {@code reranker.max-concurrent-inferences} est défini. */
    private InferenceSlot acquireInferenceSlot() throws InterruptedException {
        if (inferenceLimiter == null) {
            return () -> {};
        }
        inferenceLimiter.acquire();
        return inferenceLimiter::release;
    }

    /** Jeton de concurrence relâché en fin de bloc {@code try}. */
    private interface InferenceSlot extends AutoCloseable {
        @Override
        void close();
    }

    private List<Float> runInference(Map<String, OnnxTensor> inputs, int batchSize) throws OrtException {
        try (OrtSession.Result results = session.run(inputs)) {
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
            return scores;
        }
        // Pas de fermeture des tenseurs ici : l'appelant s'en charge dans son try-with-resources,
        // qui couvre aussi les chemins où cette méthode n'est jamais atteinte (attente du sémaphore
        // interrompue). Fermer aux deux endroits reviendrait à fermer deux fois.
    }

    /**
     * Internal record to store a document along with its calculated rerank score.
     *
     * @param document The original document.
     * @param score The calculated rerank score.
     */
    private record ScoredDocument(Document document, float score) {}
}
