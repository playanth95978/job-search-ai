package nc.opt.aiassistant.batch;

import nc.opt.aiassistant.repository.jobcopilot.PersonalDataRetentionRepository;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Job de purge des données personnelles au-delà de la durée de conservation (RGPD, art. 5-1-e :
 * limitation de la conservation).
 *
 * <p><b>Non partitionné, contrairement à la purge des offres</b> : le volume est celui des
 * utilisateurs dormants, pas des offres, donc quelques ordres de grandeur en dessous. Un seul worker
 * séquentiel suffit, et évite que deux partitions ne se disputent des suppressions en cascade sur
 * les mêmes tables parentes.</p>
 *
 * <p>Passer par un vrai job Spring Batch — plutôt qu'un simple {@code @Scheduled} — donne la trace
 * d'exécution horodatée visible dans le tableau de bord batch : c'est précisément ce qu'on doit
 * pouvoir produire pour démontrer que la politique de conservation est effectivement appliquée.</p>
 */
@Configuration
public class PurgePersonalDataBatchConfig {

    /** Nom du job, référencé par le scheduler et le garde de non-concurrence. */
    public static final String GDPR_PURGE_JOB_NAME = "purgePersonalDataJob";

    /**
     * Durée de conservation, en jours, à compter de la dernière activité de l'utilisateur.
     * 730 jours (2 ans) par défaut, aligné sur la recommandation CNIL pour les données de candidature.
     */
    @Value("${application.job-copilot.gdpr.retention-days:730}")
    private int retentionDays;

    /** Utilisateurs traités par transaction. */
    @Value("${application.job-copilot.gdpr.batch-size:100}")
    private int batchSize;

    private final PlatformTransactionManager transactionManager;

    public PurgePersonalDataBatchConfig(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    @Bean
    public Job purgePersonalDataJob(JobRepository jobRepository, Step purgePersonalDataStep) {
        return new JobBuilder(GDPR_PURGE_JOB_NAME, jobRepository).incrementer(new RunIdIncrementer()).start(purgePersonalDataStep).build();
    }

    @Bean
    public Step purgePersonalDataStep(JobRepository jobRepository, PersonalDataRetentionRepository repository) {
        return new StepBuilder("purgePersonalDataStep", jobRepository)
            .tasklet(new PurgePersonalDataTasklet(repository, retentionDays, batchSize), transactionManager)
            .build();
    }
}
