package nc.opt.aiassistant.config.ai;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Installe le modèle de reranking et son tokenizer au démarrage s'ils sont absents.
 *
 * <p>Les fichiers pèsent ~280 Mo et ne sont pas versionnés : sans ce téléchargement, tout nouveau
 * déploiement exigerait une étape manuelle. Le remplaçant de l'ancien {@code
 * downloadOnnxModelIfAbsent()} de la classe d'application, qui visait un dépôt et un chemin codés
 * en dur — donc devenu faux dès que le modèle par défaut a changé.</p>
 *
 * <p><b>Les URL suivent les chemins locaux</b> : les quatre propriétés {@code reranker.model-path},
 * {@code tokenizer-path}, {@code model-url} et {@code tokenizer-url} se règlent ensemble. Basculer
 * sur le modèle de repli ne demande donc aucun changement de code.</p>
 */
final class RerankerModelProvisioner {

    private static final Logger LOG = LoggerFactory.getLogger(RerankerModelProvisioner.class);

    private RerankerModelProvisioner() {}

    /**
     * Garantit la présence de {@code target}, en le téléchargeant depuis {@code url} au besoin.
     *
     * @throws IllegalStateException si le fichier reste absent — mieux vaut un arrêt explicite au
     *                               démarrage qu'un reranking silencieusement inopérant.
     */
    static void ensurePresent(Path target, String url, String label) {
        if (Files.exists(target)) {
            LOG.debug("{} déjà présent : {}", label, target);
            return;
        }
        if (url == null || url.isBlank()) {
            throw new IllegalStateException(
                label + " absent (" + target.toAbsolutePath() + ") et aucune URL de téléchargement configurée."
            );
        }

        if (!target.isAbsolute()) {
            // En conteneur, Jib fixe le répertoire de travail à « / » : un chemin relatif vise donc
            // la racine du système de fichiers, que l'utilisateur applicatif ne peut pas écrire.
            LOG.warn(
                "{} configuré en chemin relatif ({}) : il sera résolu depuis {} — en conteneur, " +
                    "préférer un chemin absolu sur un volume inscriptible.",
                label,
                target,
                Path.of("").toAbsolutePath()
            );
        }

        LOG.info("{} absent, téléchargement depuis {} vers {}", label, url, target.toAbsolutePath());
        // Écriture dans un fichier temporaire puis déplacement atomique : une coupure réseau en
        // cours de route laisserait sinon un fichier tronqué que le démarrage suivant considérerait
        // comme « présent », et le modèle échouerait à charger indéfiniment.
        Path temp = null;
        try {
            Files.createDirectories(target.getParent());
            temp = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".part");
            try (InputStream in = URI.create(url).toURL().openStream()) {
                Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            temp = null;
            LOG.info("{} installé : {} ({} octets)", label, target, Files.size(target));
        } catch (java.nio.file.AccessDeniedException e) {
            // Cause la plus fréquente en production, et la moins lisible dans la trace brute :
            // le chemin par défaut est relatif, et le conteneur démarre à la racine.
            throw new IllegalStateException(
                "Droits insuffisants pour écrire le " +
                    label +
                    " dans " +
                    target.toAbsolutePath() +
                    " (répertoire de travail : " +
                    Path.of("").toAbsolutePath() +
                    ", utilisateur : " +
                    System.getProperty("user.name") +
                    "). Pointer RERANKER_MODEL_PATH et RERANKER_TOKENIZER_PATH vers un volume " +
                    "inscriptible plutôt que d'élargir les droits de la racine.",
                e
            );
        } catch (Exception e) {
            throw new IllegalStateException(
                "Échec du téléchargement du " +
                    label +
                    " depuis " +
                    url +
                    " — installer le fichier manuellement dans " +
                    target.toAbsolutePath() +
                    " (voir ./scripts/download-rerankers.sh).",
                e
            );
        } finally {
            deleteQuietly(temp);
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (Exception e) {
            LOG.warn("Fichier temporaire non supprimé : {}", path);
        }
    }
}
