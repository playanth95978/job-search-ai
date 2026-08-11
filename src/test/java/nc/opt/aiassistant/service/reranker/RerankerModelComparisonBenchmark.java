package nc.opt.aiassistant.service.reranker;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Compare des cross-encoders candidats au modèle de production, à conditions strictement égales :
 * mêmes requêtes, mêmes documents, même troncature, même pipeline d'inférence.
 *
 * <p><b>Seul le tokenizer diffère</b>, et il le doit : chaque modèle a son propre vocabulaire, lui
 * en imposer un autre produirait des identifiants de tokens dénués de sens.</p>
 *
 * <p><b>Critère d'acceptation</b> : la fidélité de classement, pas la vitesse. Un modèle deux fois
 * plus rapide qui déplace le bon résultat hors du top-5 est une régression. On mesure donc l'écart
 * de classement avec le modèle actuel, qui sert de référence — étant entendu que « référence » ne
 * veut pas dire « vérité terrain » : si le candidat diverge, c'est qu'il classe <i>différemment</i>,
 * et seul un jugement humain sur de vraies requêtes peut dire lequel a raison.</p>
 *
 * <pre>{@code
 * ./scripts/download-rerankers.sh
 * ./mvnw test -Dtest=RerankerModelComparisonBenchmark -Dbenchmark=true
 * }</pre>
 */
@EnabledIfSystemProperty(named = "benchmark", matches = "true")
class RerankerModelComparisonBenchmark {

    /** Troncature de production pour les offres (JOB_OFFER_RERANK_MAX_LENGTH). */
    private static final int MAX_LENGTH = 256;

    private static final int[] DOC_COUNTS = { 20, 30, 60 };

    private static final String REFERENCE = "bge-reranker-v2-m3 (actuel)";

    /** nom -> {modèle, tokenizer}. Le premier est la référence. */
    private static final Map<String, String[]> MODELS = new LinkedHashMap<>();

    static {
        MODELS.put(REFERENCE, new String[] { "scripts/onnxmodel/model.onnx", "scripts/onnxmodel/tokenizer.json" });
        MODELS.put("bge-reranker-base", new String[] {
            "scripts/rerankers/bge-base/model.onnx",
            "scripts/rerankers/bge-base/tokenizer.json",
        });
        MODELS.put("jina-reranker-v2-multi", new String[] {
            "scripts/rerankers/jina-v2/model.onnx",
            "scripts/rerankers/jina-v2/tokenizer.json",
        });
        MODELS.put("mmarco-mMiniLMv2-L12", new String[] {
            "scripts/rerankers/mminilm/model.onnx",
            "scripts/rerankers/mminilm/tokenizer.json",
        });
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.MINUTES)
    void compareCandidatesAgainstProductionModel() throws Exception {
        System.out.printf(
            "%n=== Comparaison de rerankers — %d cœurs, maxLength=%d, %d requêtes ===%n",
            Runtime.getRuntime().availableProcessors(),
            MAX_LENGTH,
            RerankerTestCorpus.QUERIES.size()
        );

        Map<Integer, List<String>> corpus = new LinkedHashMap<>();
        for (int docs : DOC_COUNTS) {
            corpus.put(docs, RerankerTestCorpus.documents(docs));
        }

        // Scores de référence, conservés pour comparer chaque candidat au même étalon.
        Map<Integer, float[][]> referenceScores = new LinkedHashMap<>();

        System.out.printf(
            "%n%-26s %-6s %11s %11s %9s %7s %7s %8s %10s%n",
            "modèle",
            "docs",
            "total (ms)",
            "infér. (ms)",
            "docs/s",
            "top-1",
            "top-5",
            "top-10",
            "Spearman"
        );

        for (Map.Entry<String, String[]> entry : MODELS.entrySet()) {
            String name = entry.getKey();
            String modelPath = entry.getValue()[0];
            String tokenizerPath = entry.getValue()[1];

            if (!Files.exists(Paths.get(modelPath))) {
                System.out.printf("%-26s  ABSENT — lancer ./scripts/download-rerankers.sh%n", name);
                continue;
            }

            try (RerankerProbe probe = RerankerProbe.open(modelPath, tokenizerPath)) {
                for (int docs : DOC_COUNTS) {
                    List<String> documents = corpus.get(docs);
                    Measurement m = measure(probe, documents, docs);

                    boolean isReference = name.equals(REFERENCE);
                    if (isReference) {
                        referenceScores.put(docs, m.scores());
                    }

                    RerankerMaxLengthBenchmark.Fidelity fidelity = isReference
                        ? new RerankerMaxLengthBenchmark.Fidelity(1, 1, 1, 1)
                        : RerankerMaxLengthBenchmark.compare(referenceScores.get(docs), m.scores());

                    System.out.printf(
                        "%-26s %-6d %11.0f %11.0f %9.1f %7.2f %7.2f %8.2f %10.3f%n",
                        isReference ? name + " *" : name,
                        docs,
                        m.totalMs(),
                        m.inferenceMs(),
                        (docs * 1000.0) / m.totalMs(),
                        fidelity.top1(),
                        fidelity.top5(),
                        fidelity.top10(),
                        fidelity.spearman()
                    );
                }
            }
            System.out.println();
        }

        System.out.println("* référence. Fidélité = accord avec la référence, pas justesse absolue.");
    }

    /** Un passage complet sur toutes les requêtes : médiane des temps, et scores conservés. */
    private Measurement measure(RerankerProbe probe, List<String> documents, int docs) throws Exception {
        // Chauffe : le premier appel paie l'allocation des arènes ONNX et le JIT.
        probe.score(RerankerTestCorpus.QUERIES.getFirst(), documents, MAX_LENGTH);

        List<Long> totals = new ArrayList<>();
        List<Long> inferences = new ArrayList<>();
        float[][] scores = new float[RerankerTestCorpus.QUERIES.size()][];

        for (int i = 0; i < RerankerTestCorpus.QUERIES.size(); i++) {
            String query = RerankerTestCorpus.QUERIES.get(i);
            RerankerProbe.Result result = probe.timedScore(query, documents, MAX_LENGTH);
            totals.add(result.totalMs());
            inferences.add(result.inferenceMs());
            scores[i] = result.scores();
        }

        return new Measurement(median(totals), median(inferences), scores);
    }

    private double median(List<Long> values) {
        List<Long> sorted = values.stream().sorted().toList();
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }

    private record Measurement(double totalMs, double inferenceMs, float[][] scores) {}
}
