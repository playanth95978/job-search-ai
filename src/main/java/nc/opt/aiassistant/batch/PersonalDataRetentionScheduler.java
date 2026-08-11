package nc.opt.aiassistant.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Déclenche la purge quotidienne des données personnelles expirées
 * ({@link PurgePersonalDataBatchConfig}).
 *
 * <p>Créneau à 5 h, après l'ingestion (2 h) et la purge des offres (4 h), pour ne pas se disputer la
 * JVM et la base avec elles. Un garde vérifie qu'aucun de ces jobs ne tourne encore : la purge est
 * idempotente et quotidienne, sauter un run est sans conséquence sur la conformité.</p>
 *
 * <p>Désactivable via {@code application.job-copilot.gdpr.enabled} — utile en développement, où la
 * base contient des jeux de test aux dates arbitraires.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PersonalDataRetentionScheduler {

    /** Jobs à ne pas concurrencer. */
    private static final String INGESTION_JOB_NAME = "ingestJobOffersJob";

    private final JobOperator jobLauncher;
    private final JobRepository jobRepository;
    private final Job purgePersonalDataJob;

    @Value("${application.job-copilot.gdpr.enabled:true}")
    private boolean enabled;

    /** Tous les jours à 5 h. */
    @Scheduled(cron = "${application.job-copilot.gdpr.cron:0 0 5 * * *}")
    public void runGdprPurgeJob() {
        if (!enabled) {
            log.debug("Purge RGPD désactivée par configuration");
            return;
        }
        for (String job : new String[] {
            INGESTION_JOB_NAME,
            PurgeExpiredOffersBatchConfig.PURGE_JOB_NAME,
            PurgePersonalDataBatchConfig.GDPR_PURGE_JOB_NAME,
        }) {
            if (isRunning(job)) {
                log.warn("Purge RGPD annulée : le job « {} » est encore en cours", job);
                return;
            }
        }
        try {
            log.info("Démarrage de la purge RGPD des données personnelles expirées");
            JobParameters params = new JobParametersBuilder().addLong("startAt", System.currentTimeMillis()).toJobParameters();
            jobLauncher.start(purgePersonalDataJob, params);
        } catch (Exception e) {
            log.error("Échec de la purge RGPD : {}", e.getMessage(), e);
        }
    }

    private boolean isRunning(String jobName) {
        return !jobRepository.findRunningJobExecutions(jobName).isEmpty();
    }
}
