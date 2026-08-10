package nc.opt.aiassistant.batch;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.repository.jobcopilot.JobOfferRepository;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

/**
 * Supprime, lot par lot, les offres expirées d'une partition.
 *
 * <p><b>Pourquoi un tasklet et non un step chunk-oriented</b> : un {@code ItemReader} paginé lirait une
 * cible mouvante — les lignes disparaissent au fur et à mesure des suppressions, ce qui décale les
 * offsets et fait sauter des lignes. Le motif canonique pour une suppression en masse est donc un
 * tasklet qui relit toujours la « tête » du jeu restant et renvoie {@link RepeatStatus#CONTINUABLE}
 * tant qu'il reste des lignes : Spring Batch ouvre alors une transaction par invocation, ce qui donne
 * gratuitement le découpage transactionnel, la reprise et le comptage.</p>
 *
 * <p>Les offres référencées par une candidature sont exclues côté SQL (historique utilisateur, et FK
 * en RESTRICT) ; elles restent invisibles en recherche grâce au filtre d'expiration des requêtes.</p>
 */
@Slf4j
public class PurgeExpiredOffersTasklet implements Tasklet {

    private final JobOfferRepository jobOfferRepository;
    private final int partitionIndex;
    private final int gridSize;
    private final int batchSize;

    public PurgeExpiredOffersTasklet(JobOfferRepository jobOfferRepository, int partitionIndex, int gridSize, int batchSize) {
        this.jobOfferRepository = jobOfferRepository;
        this.partitionIndex = partitionIndex;
        this.gridSize = gridSize;
        this.batchSize = batchSize;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        List<UUID> batch = jobOfferRepository.findExpiredOfferIds(Instant.now(), gridSize, partitionIndex, batchSize);

        if (batch.isEmpty()) {
            return RepeatStatus.FINISHED;
        }

        // Les hits de radar d'abord : la FK radar_hit -> job_offer est en RESTRICT. Ce sont des
        // données dérivées (recalculables), contrairement aux candidatures qu'on ne touche jamais.
        int radarHits = jobOfferRepository.deleteRadarHitsByOfferIds(batch);
        int deleted = jobOfferRepository.deleteByIdIn(batch);

        // Remonte le compteur dans la StepExecution : le tableau de bord batch et les métriques
        // Spring Batch reflètent ainsi le volume réellement supprimé.
        contribution.incrementWriteCount(deleted);

        log.debug("Purge partition {}/{} : {} offre(s) et {} hit(s) radar supprimés", partitionIndex, gridSize, deleted, radarHits);

        // Lot plein : il reste probablement des lignes, on redemande un tour (nouvelle transaction).
        return batch.size() < batchSize ? RepeatStatus.FINISHED : RepeatStatus.CONTINUABLE;
    }
}
