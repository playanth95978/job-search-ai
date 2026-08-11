package nc.opt.aiassistant.service.reranker;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.util.PairList;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Exécute un cross-encoder ONNX quelconque sur des paires (requête, document).
 *
 * <p>Réplique fidèlement le pipeline de {@code OnnxRerankerService} — mêmes options de session,
 * même troncature, même construction de tenseurs — pour que les mesures soient transposables à la
 * production. Chaque modèle utilise en revanche <b>son propre</b> tokenizer : les vocabulaires
 * diffèrent d'un modèle à l'autre, en imposer un seul produirait des identifiants de tokens
 * dénués de sens et des scores ininterprétables.</p>
 *
 * <p>Gère aussi les modèles exigeant {@code token_type_ids} (BERT et dérivés), absent de
 * XLM-RoBERTa : le jeu d'entrées est déduit du graphe plutôt que supposé.</p>
 */
final class RerankerProbe implements AutoCloseable {

    private final OrtEnvironment env;
    private final OrtSession session;
    private final HuggingFaceTokenizer tokenizer;
    private final Set<String> expectedInputs;

    private RerankerProbe(OrtEnvironment env, OrtSession session, HuggingFaceTokenizer tokenizer) throws Exception {
        this.env = env;
        this.session = session;
        this.tokenizer = tokenizer;
        this.expectedInputs = session.getInputNames();
    }

    static RerankerProbe open(String modelPath, String tokenizerPath) throws Exception {
        Path model = Paths.get(modelPath);
        if (!Files.exists(model)) {
            throw new IllegalStateException("Modèle introuvable : " + model.toAbsolutePath());
        }
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
        opts.setIntraOpNumThreads(Runtime.getRuntime().availableProcessors());
        opts.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.PARALLEL);
        opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
        OrtSession session = env.createSession(modelPath, opts);
        HuggingFaceTokenizer tokenizer = HuggingFaceTokenizer.newInstance(Paths.get(tokenizerPath));
        return new RerankerProbe(env, session, tokenizer);
    }

    /** Scores bruts, un par document, dans l'ordre d'entrée. */
    float[] score(String query, List<String> documents, int maxLength) throws Exception {
        return run(query, documents, maxLength).scores();
    }

    /** Scores et temps en un seul passage : mesurer et scorer séparément doublerait le coût du banc. */
    Result timedScore(String query, List<String> documents, int maxLength) throws Exception {
        return run(query, documents, maxLength);
    }

    private Result run(String query, List<String> documents, int maxLength) throws Exception {
        long start = System.nanoTime();

        PairList<String, String> batch = new PairList<>(documents.size());
        for (String doc : documents) {
            batch.add(query, doc);
        }
        Encoding[] encodings = tokenizer.batchEncode(batch);
        int batchSize = encodings.length;

        long[][] inputIds = new long[batchSize][maxLength];
        long[][] attentionMask = new long[batchSize][maxLength];
        long[][] tokenTypeIds = new long[batchSize][maxLength];
        for (int i = 0; i < batchSize; i++) {
            copy(encodings[i].getIds(), inputIds[i], maxLength);
            copy(encodings[i].getAttentionMask(), attentionMask[i], maxLength);
            copy(encodings[i].getTypeIds(), tokenTypeIds[i], maxLength);
        }

        Map<String, OnnxTensor> inputs = new HashMap<>();
        inputs.put("input_ids", OnnxTensor.createTensor(env, inputIds));
        inputs.put("attention_mask", OnnxTensor.createTensor(env, attentionMask));
        if (expectedInputs.contains("token_type_ids")) {
            inputs.put("token_type_ids", OnnxTensor.createTensor(env, tokenTypeIds));
        }

        long inferenceStart = System.nanoTime();
        float[] scores = new float[batchSize];
        try (OrtSession.Result results = session.run(inputs)) {
            Object raw = results.get(0).getValue();
            // Les têtes de classification sortent en [batch, 1] ; certains modèles aplatissent en [batch].
            if (raw instanceof float[][] matrix) {
                for (int i = 0; i < batchSize; i++) {
                    scores[i] = matrix[i][0];
                }
            } else if (raw instanceof float[] vector) {
                System.arraycopy(vector, 0, scores, 0, batchSize);
            } else {
                throw new IllegalStateException("Sortie ONNX inattendue : " + raw.getClass());
            }
        } finally {
            inputs.values().forEach(OnnxTensor::close);
        }
        long end = System.nanoTime();

        return new Result(scores, TimeUnit.NANOSECONDS.toMillis(end - start), TimeUnit.NANOSECONDS.toMillis(end - inferenceStart));
    }

    private static void copy(long[] source, long[] target, int maxLength) {
        if (source == null) {
            return;
        }
        int len = Math.min(source.length, maxLength);
        if (len > 0) {
            System.arraycopy(source, 0, target, 0, len);
        }
    }

    @Override
    public void close() throws Exception {
        session.close();
        tokenizer.close();
        // L'OrtEnvironment est un singleton partagé par le processus : le fermer ici invaliderait
        // les sondes ouvertes ensuite dans le même banc.
    }

    record Result(float[] scores, long totalMs, long inferenceMs) {}
}
