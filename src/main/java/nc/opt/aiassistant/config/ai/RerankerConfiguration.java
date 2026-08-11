package nc.opt.aiassistant.config.ai;

import nc.opt.aiassistant.service.metrics.RagMetrics;
import nc.opt.aiassistant.service.reranker.OnnxRerankerService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Déclare le reranker ONNX.
 *
 * <p>{@link OnnxRerankerService} n'est plus annoté {@code @Service} : son câblage est explicite ici,
 * ce qui rassemble en un seul endroit les réglages qui décident de son coût — modèle, tokenizer,
 * parallélisme, concurrence — au lieu de les disperser en {@code @Value} dans la classe.</p>
 *
 * <p><b>Modèle par défaut : {@code bge-reranker-base}</b> (278 M paramètres, INT8, multilingue),
 * retenu après mesure : 2,9× plus rapide que {@code bge-reranker-v2-m3} avec la meilleure fidélité
 * de classement des candidats évalués. Repli disponible sur {@code mmarco-mMiniLMv2-L12} (118 M,
 * 7,3×) si le CPU du serveur reste insuffisant.</p>
 *
 * <p><b>Les modèles ne sont pas versionnés</b> (~280 Mo) : {@code ./scripts/download-rerankers.sh}
 * doit avoir été exécuté sur la machine, sinon le démarrage échoue avec un message explicite.</p>
 */
@Configuration
public class RerankerConfiguration {

    @Value("${reranker.model-path:scripts/rerankers/bge-base/model.onnx}")
    private String modelPath;

    @Value("${reranker.tokenizer-path:scripts/rerankers/bge-base/tokenizer.json}")
    private String tokenizerPath;

    /** Threads ONNX par inférence ; 0 = tous les cœurs. Mesuré : brider dégrade débit et latence. */
    @Value("${reranker.intra-op-threads:0}")
    private int intraOpThreads;

    /** Inférences simultanées ; 0 = illimité. Borne utile pour stabiliser le p95 sous charge. */
    @Value("${reranker.max-concurrent-inferences:0}")
    private int maxConcurrentInferences;

    /** Source du modèle, à changer en même temps que {@code model-path}. */
    @Value("${reranker.model-url:https://huggingface.co/onnx-community/bge-reranker-base-ONNX/resolve/main/onnx/model_int8.onnx}")
    private String modelUrl;

    @Value("${reranker.tokenizer-url:https://huggingface.co/onnx-community/bge-reranker-base-ONNX/resolve/main/tokenizer.json}")
    private String tokenizerUrl;

    /**
     * L'instance est construite ici, mais son cycle de vie reste géré par Spring : les
     * {@code @PostConstruct} / {@code @PreDestroy} de la classe s'appliquent aussi aux beans issus
     * d'une méthode {@code @Bean}. Appeler {@code init()} à la main ouvrirait donc une seconde
     * session ONNX — plusieurs centaines de mégaoctets perdus, sans erreur visible.
     */
    @Bean
    public OnnxRerankerService onnxRerankerService(RagMetrics ragMetrics) {
        return new OnnxRerankerService(ragMetrics, intraOpThreads, maxConcurrentInferences, modelPath, tokenizerPath);
    }
}
