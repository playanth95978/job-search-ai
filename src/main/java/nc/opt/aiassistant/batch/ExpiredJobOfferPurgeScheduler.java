package nc.opt.aiassistant.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Déclenche la purge quotidienne des offres expirées ({@link PurgeExpiredOffersBatchConfig}).
 *
 * <p><b>Non-concurrence avec l'ingestion</b> — deux protections complémentaires :</p>
 * <ol>
 *   <li>un créneau distinct : l'ingestion démarre à 2 h ({@link JobOfferBatchScheduler}), la purge à 4 h ;</li>
 *   <li>un garde à l'exécution : si l'ingestion tourne encore (run plus long que prévu, relance
 *       manuelle…), la purge est annulée plutôt que de rivaliser avec elle pour la JVM et la base.
 *       La purge est idempotente et quotidienne : sauter un run est sans conséquence.</li>
 * </ol>
 *
 * <p>Le garde couvre aussi la purge elle-même, pour qu'un run encore en cours ne soit pas doublé.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpiredJobOfferPurgeScheduler {

    /** Job d'ingestion à ne pas concurrencer (voir {@link JobOfferBatchConfig}). */
    private static final String INGESTION_JOB_NAME = "ingestJobOffersJob";

    private final JobOperator jobLauncher;
    private final JobRepository jobRepository;
    private final Job purgeExpiredOffersJob;

    /** Tous les jours à 4 h, soit deux heures après le démarrage de l'ingestion. */
    @Scheduled(cron = "${application.job-copilot.purge.cron:0 0 4 * * *}")
    public void runPurgeJob() {
        if (isRunning(INGESTION_JOB_NAME)) {
            log.warn("Purge des offres expirées annulée : le job « {} » est encore en cours", INGESTION_JOB_NAME);
            return;
        }
        if (isRunning(PurgeExpiredOffersBatchConfig.PURGE_JOB_NAME)) {
            log.warn("Purge des offres expirées annulée : un run précédent est toujours en cours");
            return;
        }
        try {
            log.info("Démarrage de la purge des offres expirées");
            JobParameters params = new JobParametersBuilder().addLong("startAt", System.currentTimeMillis()).toJobParameters();
            jobLauncher.start(purgeExpiredOffersJob, params);
        } catch (Exception e) {
            log.error("Échec de la purge des offres expirées : {}", e.getMessage(), e);
        }
    }

    private boolean isRunning(String jobName) {
        return !jobRepository.findRunningJobExecutions(jobName).isEmpty();
    }
}
