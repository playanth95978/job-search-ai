package nc.opt.aiassistant.batch;

import nc.opt.aiassistant.repository.jobcopilot.JobOfferRepository;
import nc.opt.aiassistant.service.jobcopilot.OfferLivenessChecker;
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
 * Job de vérification de liveness des offres : teste que les {@code apply_url} stockées répondent
 * encore et marque expirées (⇒ purgeables) celles qui renvoient un 404/410.
 *
 * <p>Même structure que la purge ({@link PurgeExpiredOffersBatchConfig}) : un step maître partitionné
 * par hachage d'identifiant ({@link ExpiredOfferPartitioner}) délègue à des workers tasklet
 * ({@link VerifyOfferLivenessTasklet}) exécutés en parallèle sur un executor dédié (threads virtuels,
 * les workers étant bloquants sur I/O réseau). Chaque worker relit la tête de sa partition jusqu'à
 * épuisement, ce qui donne le découpage transactionnel, le suivi et la reprise via Spring Batch.
 * Le job est déclenché à la demande depuis le tableau de bord batch.</p>
 */
@Configuration
public class VerifyOfferLivenessBatchConfig {

    /** Nom du job, visible et déclenchable dans le tableau de bord batch. */
    public static final String LIVENESS_JOB_NAME = "verifyOfferLivenessJob";

    @Value("${application.job-copilot.liveness.batch-size:25}")
    private int batchSize;

    @Value("${application.job-copilot.liveness.concurrency:4}")
    private int concurrency;

    @Value("${application.job-copilot.liveness.recheck-hours:24}")
    private int recheckHours;

    @Value("${application.job-copilot.liveness.max-per-run:2000}")
    private int maxPerRun;

    private final PlatformTransactionManager transactionManager;

    public VerifyOfferLivenessBatchConfig(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    @Bean
    public Job verifyOfferLivenessJob(JobRepository jobRepository, Step verifyOfferLivenessStep) {
        return new JobBuilder(LIVENESS_JOB_NAME, jobRepository).incrementer(new RunIdIncrementer()).start(verifyOfferLivenessStep).build();
    }

    @Bean
    public Step verifyOfferLivenessStep(JobRepository jobRepository, Step livenessWorkerStep, TaskExecutor livenessTaskExecutor) {
        return new StepBuilder("verifyOfferLivenessStep", jobRepository)
            .partitioner(livenessWorkerStep.getName(), new ExpiredOfferPartitioner())
            .step(livenessWorkerStep)
            .gridSize(concurrency)
            .taskExecutor(livenessTaskExecutor)
            .build();
    }

    @Bean
    public Step livenessWorkerStep(JobRepository jobRepository, VerifyOfferLivenessTasklet verifyOfferLivenessTasklet) {
        return new StepBuilder("livenessWorkerStep", jobRepository).tasklet(verifyOfferLivenessTasklet, transactionManager).build();
    }

    @Bean
    @StepScope
    public VerifyOfferLivenessTasklet verifyOfferLivenessTasklet(
        JobOfferRepository jobOfferRepository,
        OfferLivenessChecker livenessChecker,
        @Value("#{stepExecutionContext['" + ExpiredOfferPartitioner.PARTITION_INDEX + "']}") Integer partitionIndex,
        @Value("#{stepExecutionContext['" + ExpiredOfferPartitioner.GRID_SIZE + "']}") Integer gridSize
    ) {
        return new VerifyOfferLivenessTasklet(
            jobOfferRepository,
            livenessChecker,
            partitionIndex,
            gridSize,
            batchSize,
            recheckHours,
            maxPerRun
        );
    }

    /** Executor dédié à la liveness (threads virtuels : workers bloquants sur I/O réseau). */
    @Bean
    public TaskExecutor livenessTaskExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("offer-liveness-");
        executor.setVirtualThreads(true);
        executor.setConcurrencyLimit(concurrency);
        return executor;
    }
}
