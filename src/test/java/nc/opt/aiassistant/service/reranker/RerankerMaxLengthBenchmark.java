package nc.opt.aiassistant.service.reranker;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Balaye la longueur de séquence du reranker : 64, 128 et 256 tokens.
 *
 * <p>L'attention d'un transformer est en O(n²) sur la longueur, les couches denses en O(n) : réduire
 * la troncature est donc théoriquement le levier le plus rentable après le choix du modèle. Mais
 * tronquer, c'est jeter du texte — un document dont la compétence décisive apparaît au-delà de la
 * coupe devient invisible au modèle. On mesure donc aussi ce que le classement y perd, en prenant
 * la configuration actuelle (256) pour référence.</p>
 *
 * <pre>{@code ./mvnw test -Dtest=RerankerMaxLengthBenchmark -Dbenchmark=true}</pre>
 */
@EnabledIfSystemProperty(named = "benchmark", matches = "true")
class RerankerMaxLengthBenchmark {

    private static final int REFERENCE_LENGTH = 256;
    private static final int[] LENGTHS = { 64, 128, 256 };
    private static final int[] DOC_COUNTS = { 20, 30, 60 };
    private static final int RUNS = 3;

    @Test
    @Timeout(value = 60, unit = TimeUnit.MINUTES)
    void sweepSequenceLength() throws Exception {
        // Modèle balayable : le réglage de troncature doit être choisi sur celui qu'on va déployer,
        // pas sur l'ancien — deux modèles ne perdent pas la même chose à la même coupe.
        String model = System.getProperty("reranker.model", "scripts/onnxmodel/model.onnx");
        String tokenizer = System.getProperty("reranker.tokenizer", "scripts/onnxmodel/tokenizer.json");
        System.out.printf("%nModèle balayé : %s%n", model);
        RerankerProbe probe = RerankerProbe.open(model, tokenizer);

        try {
            System.out.printf("%n=== Longueur de séquence — %d cœurs ===%n", Runtime.getRuntime().availableProcessors());
            System.out.printf(
                "%-6s %-8s %12s %12s %10s %8s %8s %8s %10s%n",
                "docs",
                "tokens",
                "total (ms)",
                "infér. (ms)",
                "docs/s",
                "top-1",
                "top-5",
                "top-10",
                "Spearman"
            );

            for (int docs : DOC_COUNTS) {
                List<String> documents = RerankerTestCorpus.documents(docs);

                // Référence : le réglage de production, moyenné sur toutes les requêtes.
                float[][] reference = scoreAllQueries(probe, documents, REFERENCE_LENGTH);

                for (int length : LENGTHS) {
                    run(probe, documents, docs, length, reference);
                }
                System.out.println();
            }
        } finally {
            probe.close();
        }
    }

    private void run(RerankerProbe probe, List<String> documents, int docs, int length, float[][] reference) throws Exception {
        // Chauffe hors mesure.
        probe.score(RerankerTestCorpus.QUERIES.getFirst(), documents, length);

        long total = 0;
        long inference = 0;
        for (int run = 0; run < RUNS; run++) {
            for (String query : RerankerTestCorpus.QUERIES) {
                RerankerProbe.Result result = probe.timedScore(query, documents, length);
                total += result.totalMs();
                inference += result.inferenceMs();
            }
        }

        int calls = RUNS * RerankerTestCorpus.QUERIES.size();
        double totalMs = total / (double) calls;
        double inferenceMs = inference / (double) calls;
        double docsPerSecond = totalMs == 0 ? 0 : (docs * 1000.0) / totalMs;

        float[][] candidate = scoreAllQueries(probe, documents, length);
        Fidelity fidelity = compare(reference, candidate);

        System.out.printf(
            "%-6d %-8d %12.0f %12.0f %10.1f %8.2f %8.2f %8.2f %10.3f%n",
            docs,
            length,
            totalMs,
            inferenceMs,
            docsPerSecond,
            fidelity.top1(),
            fidelity.top5(),
            fidelity.top10(),
            fidelity.spearman()
        );
    }

    private float[][] scoreAllQueries(RerankerProbe probe, List<String> documents, int length) throws Exception {
        float[][] scores = new float[RerankerTestCorpus.QUERIES.size()][];
        for (int i = 0; i < RerankerTestCorpus.QUERIES.size(); i++) {
            scores[i] = probe.score(RerankerTestCorpus.QUERIES.get(i), documents, length);
        }
        return scores;
    }

    /** Moyenne les métriques sur l'ensemble des requêtes. */
    static Fidelity compare(float[][] reference, float[][] candidate) {
        double top1 = 0;
        double top5 = 0;
        double top10 = 0;
        double spearman = 0;
        for (int i = 0; i < reference.length; i++) {
            int[] refRank = RankingFidelity.ranking(reference[i]);
            int[] candRank = RankingFidelity.ranking(candidate[i]);
            top1 += RankingFidelity.top1Agreement(refRank, candRank);
            top5 += RankingFidelity.topKOverlap(refRank, candRank, 5);
            top10 += RankingFidelity.topKOverlap(refRank, candRank, 10);
            spearman += RankingFidelity.spearman(reference[i], candidate[i]);
        }
        int n = reference.length;
        return new Fidelity(top1 / n, top5 / n, top10 / n, spearman / n);
    }

    record Fidelity(double top1, double top5, double top10, double spearman) {}
}
