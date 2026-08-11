package nc.opt.aiassistant.service.reranker;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import nc.opt.aiassistant.service.metrics.RagMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.document.Document;

/**
 * Banc de mesure de la montée en charge du reranker ONNX — la base de référence avant d'accepter ou
 * de rejeter l'hypothèse « {@code intraOpNumThreads = tous les cœurs} fait s'effondrer la latence en
 * concurrence ».
 *
 * <p>Aucune base ni réseau : seul le modèle ONNX est chargé. On mesure donc le goulot CPU isolé,
 * sans le bruit de PostgreSQL, d'Ollama ou de HTTP.</p>
 *
 * <p><b>Exclu des exécutions normales</b> (plusieurs minutes, 570 Mo de modèle chargé plusieurs
 * fois). Pour le lancer :</p>
 *
 * <pre>{@code ./mvnw test -Dtest=RerankerConcurrencyBenchmark -Dbenchmark=true}</pre>
 */
@EnabledIfSystemProperty(named = "benchmark", matches = "true")
class RerankerConcurrencyBenchmark {

    /** Le chemin chaud reranke {@code limit * 2} documents ; limit = 10 dans la recherche d'offres. */
    private static final int DOCS_PER_SEARCH = 20;

    /** Longueur retenue pour les offres (JOB_OFFER_RERANK_MAX_LENGTH). */
    private static final int MAX_LENGTH = 256;

    private static final int WARMUP_ITERATIONS = 3;
    private static final int MEASURED_SEARCHES = 12;
    private static final int[] CONCURRENCY_LEVELS = { 1, 2, 4, 8 };

    private static final String QUERY = "développeur java spring boot expérimenté microservices";

    // Le projet impose un timeout global de 15 s par test (junit-platform.properties) : un banc qui
    // charge trois fois un modele de 570 Mo le depasse forcement. On l'outrepasse ici uniquement.
    @Test
    @Timeout(value = 60, unit = TimeUnit.MINUTES)
    void compareThreadConfigurations() throws Exception {
        int cores = Runtime.getRuntime().availableProcessors();
        System.out.printf("%n=== Banc reranker — %d cœurs, %d docs/recherche, maxLength=%d ===%n", cores, DOCS_PER_SEARCH, MAX_LENGTH);

        // Configurations comparées : l'actuelle, puis deux brides candidates.
        List<Config> configs = List.of(
            new Config("ACTUEL (intraOp=" + cores + ", non borné)", 0, 0),
            new Config("intraOp=2, non borné", 2, 0),
            new Config("intraOp=2, max 4 simultanées", 2, 4)
        );

        for (Config config : configs) {
            measure(config);
        }
    }

    private void measure(Config config) throws Exception {
        OnnxRerankerService reranker = new OnnxRerankerService(
            new RagMetrics(new SimpleMeterRegistry()),
            config.intraOpThreads(),
            config.maxConcurrent()
        );
        reranker.init();
        try {
            System.out.printf("%n--- %s ---%n", config.label());
            System.out.printf("%-12s %10s %10s %10s %12s%n", "concurrence", "p50 (ms)", "p95 (ms)", "max (ms)", "débit (/s)");

            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                reranker.rerank(QUERY, documents(), DOCS_PER_SEARCH, MAX_LENGTH);
            }

            for (int concurrency : CONCURRENCY_LEVELS) {
                report(concurrency, runAtConcurrency(reranker, concurrency));
            }
        } finally {
            reranker.close();
        }
    }

    /** Lance {@link #MEASURED_SEARCHES} recherches réparties sur {@code concurrency} threads. */
    private List<Long> runAtConcurrency(OnnxRerankerService reranker, int concurrency) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        try {
            List<Callable<Long>> tasks = new ArrayList<>();
            for (int i = 0; i < MEASURED_SEARCHES; i++) {
                tasks.add(() -> {
                    long start = System.nanoTime();
                    reranker.rerank(QUERY, documents(), DOCS_PER_SEARCH, MAX_LENGTH);
                    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
                });
            }

            long wallStart = System.nanoTime();
            List<Future<Long>> futures = pool.invokeAll(tasks);
            long wallMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - wallStart);

            List<Long> latencies = new ArrayList<>();
            for (Future<Long> f : futures) {
                latencies.add(f.get());
            }
            latencies.add(-wallMs); // le temps mural voyage en dernier, encodé négatif
            return latencies;
        } finally {
            pool.shutdownNow();
        }
    }

    private void report(int concurrency, List<Long> raw) {
        long wallMs = -raw.removeLast();
        List<Long> sorted = raw.stream().sorted().toList();

        double throughput = wallMs == 0 ? 0 : (MEASURED_SEARCHES * 1000.0) / wallMs;
        System.out.printf(
            "%-12d %10d %10d %10d %12.2f%n",
            concurrency,
            percentile(sorted, 50),
            percentile(sorted, 95),
            sorted.getLast(),
            throughput
        );
    }

    private long percentile(List<Long> sorted, int p) {
        int index = (int) Math.ceil((p / 100.0) * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    /** Documents de taille réaliste : un search_text d'offre fait quelques centaines de mots. */
    private List<Document> documents() {
        List<Document> docs = new ArrayList<>(DOCS_PER_SEARCH);
        for (int i = 0; i < DOCS_PER_SEARCH; i++) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("source", "JOB_OFFER");
            docs.add(new Document(UUID.randomUUID().toString(), offerText(i), metadata));
        }
        return docs;
    }

    private String offerText(int i) {
        return (
            "OFFRE D'EMPLOI: Développeur Java Senior " +
            i +
            "\nENTREPRISE: Société Exemple " +
            i +
            "\nLOCALISATION: Nouméa\nTYPE DE CONTRAT: CDI\nNIVEAU D'EXPÉRIENCE: confirmé\n" +
            "DESCRIPTION DU POSTE: Vous rejoignez une équipe produit pour concevoir et maintenir des " +
            "services Spring Boot déployés en conteneurs. Vous participez aux revues de code, à " +
            "l'amélioration continue de la qualité et à la mise en place de l'observabilité. " +
            "Compétences attendues : Java, Spring, PostgreSQL, Docker, tests automatisés, CI/CD. "
        ).repeat(3);
    }

    private record Config(String label, int intraOpThreads, int maxConcurrent) {}
}
