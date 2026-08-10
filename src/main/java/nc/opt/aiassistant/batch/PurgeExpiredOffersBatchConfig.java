package nc.opt.aiassistant.batch;

import nc.opt.aiassistant.repository.jobcopilot.JobOfferRepository;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Job de purge des offres d'emploi expirées.
 *
 * <p>Structure identique à l'ingestion ({@link JobOfferBatchConfig}) : un step maître partitionné
 * délègue à des workers exécutés en parallèle sur un {@link TaskExecutor} dédié. Les partitions sont
 * réparties par hachage d'identifiant ({@link ExpiredOfferPartitioner}), donc disjointes : aucun
 * risque de double traitement ni de contention de verrous.</p>
 *
 * <p>Chaque worker est un step <b>tasklet</b> ({@link PurgeExpiredOffersTasklet}) qui supprime un lot
 * par transaction jusqu'à épuisement de sa partition. Passer par un vrai job Spring Batch apporte le
 * suivi d'exécution (visible dans le tableau de bord batch), les compteurs, la reprise et la
 * détection de concurrence — que le {@link ExpiredJobOfferPurgeScheduler} exploite pour ne jamais
 * tourner en même temps que l'ingestion.</p>
 */
@Configuration
public class PurgeExpiredOffersBatchConfig {

    /** Nom du job, référencé par le scheduler et le garde de non-concurrence. */
    public static final String PURGE_JOB_NAME = "purgeExpiredOffersJob";

    /** Offres supprimées par transaction. */
    @Value("${application.job-copilot.purge.batch-size:500}")
    private int batchSize;

    /** Partitions purgées en parallèle ; rester sous la taille du pool JDBC. */
    @Value("${application.job-copilot.purge.concurrency:4}")
    private int concurrency;

    private final PlatformTransactionManager transactionManager;

    public PurgeExpiredOffersBatchConfig(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    @Bean
    public Job purgeExpiredOffersJob(JobRepository jobRepository, Step purgeExpiredOffersStep) {
        return new JobBuilder(PURGE_JOB_NAME, jobRepository).incrementer(new RunIdIncrementer()).start(purgeExpiredOffersStep).build();
    }

    @Bean
    public Step purgeExpiredOffersStep(JobRepository jobRepository, Step purgeWorkerStep, TaskExecutor purgeTaskExecutor) {
        return new StepBuilder("purgeExpiredOffersStep", jobRepository)
            .partitioner(purgeWorkerStep.getName(), new ExpiredOfferPartitioner())
            .step(purgeWorkerStep)
            .gridSize(concurrency)
            .taskExecutor(purgeTaskExecutor)
            .build();
    }

    @Bean
    public Step purgeWorkerStep(JobRepository jobRepository, PurgeExpiredOffersTasklet purgeExpiredOffersTasklet) {
        return new StepBuilder("purgeWorkerStep", jobRepository).tasklet(purgeExpiredOffersTasklet, transactionManager).build();
    }

    @Bean
    @StepScope
    public PurgeExpiredOffersTasklet purgeExpiredOffersTasklet(
        JobOfferRepository jobOfferRepository,
        @Value("#{stepExecutionContext['" + ExpiredOfferPartitioner.PARTITION_INDEX + "']}") Integer partitionIndex,
        @Value("#{stepExecutionContext['" + ExpiredOfferPartitioner.GRID_SIZE + "']}") Integer gridSize
    ) {
        return new PurgeExpiredOffersTasklet(jobOfferRepository, partitionIndex, gridSize, batchSize);
    }

    /**
     * Executor dédié à la purge, distinct de {@code batchTaskExecutor} (ingestion) : les deux jobs ne
     * partagent jamais le même pool. Threads virtuels, les workers étant bloquants sur I/O JDBC.
     */
    @Bean
    public TaskExecutor purgeTaskExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("offer-purge-");
        executor.setVirtualThreads(true);
        executor.setConcurrencyLimit(concurrency);
        return executor;
    }
}
