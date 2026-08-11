package nc.opt.aiassistant.service.reranker;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.util.PairList;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Ventile le coût d'un rerank entre ses trois phases : tokenisation, construction des tenseurs,
 * inférence ONNX.
 *
 * <p><b>Pourquoi ce banc</b> : {@code OnnxRerankerService} journalise « Tokenization time » à partir
 * d'un chronomètre démarré <i>avant</i> la tokenisation et arrêté <i>après</i> l'inférence. Ce log
 * additionne donc les trois phases sous une étiquette qui n'en désigne qu'une, et laisse croire que
 * la tokenisation domine. On mesure ici chaque phase séparément pour savoir où porter l'effort.</p>
 *
 * <p>Mesure aussi l'effet du nombre de documents (30 contre 60) : le coût d'un cross-encoder est
 * théoriquement linéaire en nombre de paires, cette hypothèse mérite d'être vérifiée.</p>
 *
 * <pre>{@code ./mvnw test -Dtest=RerankerPhaseBreakdownBenchmark -Dbenchmark=true}</pre>
 */
@EnabledIfSystemProperty(named = "benchmark", matches = "true")
class RerankerPhaseBreakdownBenchmark {

    private static final int MAX_LENGTH = 256;
    private static final int RUNS = 5;
    private static final String QUERY = "développeur java spring boot expérimenté microservices";

    private OrtEnvironment env;
    private OrtSession session;
    private HuggingFaceTokenizer tokenizer;

    @Test
    @Timeout(value = 30, unit = TimeUnit.MINUTES)
    void breakdownByPhaseAndBatchSize() throws Exception {
        env = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
        opts.setIntraOpNumThreads(Runtime.getRuntime().availableProcessors());
        opts.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.PARALLEL);
        opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
        session = env.createSession("scripts/onnxmodel/model.onnx", opts);
        tokenizer = HuggingFaceTokenizer.newInstance(Paths.get("scripts/onnxmodel/tokenizer.json"));

        try {
            System.out.printf(
                "%n=== Ventilation par phase — %d cœurs, maxLength=%d ===%n",
                Runtime.getRuntime().availableProcessors(),
                MAX_LENGTH
            );
            System.out.printf("%-8s %14s %14s %14s %12s%n", "docs", "tokenisation", "tenseurs", "inférence", "part infér.");

            for (int docs : new int[] { 20, 30, 60 }) {
                measure(docs);
            }
        } finally {
            session.close();
            tokenizer.close();
            env.close();
        }
    }

    private void measure(int docs) throws Exception {
        PairList<String, String> batch = batch(docs);

        // Chauffe : le premier passage paie le JIT et l'allocation des arènes ONNX.
        for (int i = 0; i < 2; i++) {
            runOnce(batch, docs);
        }

        long tokenize = 0;
        long tensors = 0;
        long inference = 0;
        for (int i = 0; i < RUNS; i++) {
            long[] phases = runOnce(batch, docs);
            tokenize += phases[0];
            tensors += phases[1];
            inference += phases[2];
        }

        double tok = tokenize / (double) RUNS;
        double ten = tensors / (double) RUNS;
        double inf = inference / (double) RUNS;
        double share = (100 * inf) / (tok + ten + inf);

        System.out.printf("%-8d %11.0f ms %11.0f ms %11.0f ms %10.1f %%%n", docs, tok, ten, inf, share);
    }

    /** @return {tokenisation, construction des tenseurs, inférence} en millisecondes. */
    private long[] runOnce(PairList<String, String> batch, int docs) throws Exception {
        long t0 = System.nanoTime();
        Encoding[] encodings = tokenizer.batchEncode(batch);
        long t1 = System.nanoTime();

        long[][] inputIds = new long[docs][MAX_LENGTH];
        long[][] attentionMask = new long[docs][MAX_LENGTH];
        for (int i = 0; i < docs; i++) {
            long[] ids = encodings[i].getIds();
            long[] mask = encodings[i].getAttentionMask();
            int len = Math.min(ids.length, MAX_LENGTH);
            if (len > 0) {
                System.arraycopy(ids, 0, inputIds[i], 0, len);
                System.arraycopy(mask, 0, attentionMask[i], 0, len);
            }
        }
        Map<String, OnnxTensor> inputs = new HashMap<>();
        inputs.put("input_ids", OnnxTensor.createTensor(env, inputIds));
        inputs.put("attention_mask", OnnxTensor.createTensor(env, attentionMask));
        long t2 = System.nanoTime();

        try (OrtSession.Result result = session.run(inputs)) {
            result.get(0).getValue();
        } finally {
            inputs.values().forEach(OnnxTensor::close);
        }
        long t3 = System.nanoTime();

        return new long[] {
            TimeUnit.NANOSECONDS.toMillis(t1 - t0),
            TimeUnit.NANOSECONDS.toMillis(t2 - t1),
            TimeUnit.NANOSECONDS.toMillis(t3 - t2),
        };
    }

    private PairList<String, String> batch(int docs) {
        PairList<String, String> batch = new PairList<>(docs);
        for (int i = 0; i < docs; i++) {
            batch.add(QUERY, offerText(i));
        }
        return batch;
    }

    private String offerText(int i) {
        return (
            "OFFRE D'EMPLOI: Développeur Java Senior " +
            i +
            "\nENTREPRISE: Société Exemple " +
            i +
            "\nLOCALISATION: Nouméa\nTYPE DE CONTRAT: CDI\nNIVEAU D'EXPÉRIENCE: confirmé\n" +
            "DESCRIPTION DU POSTE: Vous rejoignez une équipe produit pour concevoir et maintenir des " +
            "services Spring Boot déployés en conteneurs. Compétences attendues : Java, Spring, " +
            "PostgreSQL, Docker, tests automatisés, CI/CD. "
        ).repeat(3);
    }
}
