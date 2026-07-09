package nc.opt.aiassistant.batch;

import java.util.List;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.repository.jobcopilot.JobOfferRepository;
import nc.opt.aiassistant.service.jobcopilot.JobOfferIngestionService;
import nc.opt.aiassistant.service.jobcopilot.ats.AdzunaConnector;
import nc.opt.aiassistant.service.jobcopilot.ats.EmploiNcConnector;
import nc.opt.aiassistant.service.jobcopilot.ats.FranceTravailConnector;
import nc.opt.aiassistant.service.jobcopilot.ats.RemoteOkConnector;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.EnableJdbcJobRepository;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Mass-ingestion batch for job offers.
 *
 * <p>The France Travail step is <b>partitioned by ROME grand domaine</b>: each of the 14 domaines
 * runs as an independent worker step issuing its own {@code grandDomaine}-filtered query. This both
 * covers every job category and keeps each query under the API's 3150-result hard cap (a single
 * unfiltered query could never see more than 3150 offers). Workers run on virtual threads with a
 * bounded concurrency to respect the API rate limits.</p>
 *
 * <p>Throughput levers vs. the previous design: chunk-level upsert + dedup in one {@code IN} query
 * (see {@link JobOfferBatchWriter}) instead of one SELECT per offer, large chunks, and
 * {@code faultTolerant} so a single bad page/offer skips instead of killing the whole job.</p>
 *
 * <p>Embedding generation is intentionally <b>not</b> part of this job — it is handled out-of-band by
 * the scheduled embedding poller, so ingestion stays fast and restartable.</p>
 */
@Configuration
@EnableBatchProcessing
@EnableJdbcJobRepository
public class JobOfferBatchConfig {

    private static final int CHUNK_SIZE = 200;
    private static final int SKIP_LIMIT = 100;
    /** Concurrent France Travail partitions; bounded to stay within the API rate limits. */
    private static final int PARTITION_CONCURRENCY = 4;

    private final JobOfferIngestionService ingestionService;
    private final JobOfferBatchWriter jobOfferBatchWriter;
    private final BatchSkipListener skipListener;
    private final PlatformTransactionManager transactionManager;

    public JobOfferBatchConfig(
        PlatformTransactionManager transactionManager,
        JobOfferIngestionService ingestionService,
        JobOfferBatchWriter jobOfferBatchWriter,
        BatchSkipListener skipListener
    ) {
        this.transactionManager = transactionManager;
        this.ingestionService = ingestionService;
        this.jobOfferBatchWriter = jobOfferBatchWriter;
        this.skipListener = skipListener;
    }

    @Bean
    public Job ingestJobOffersJob(
        JobRepository jobRepository,
        Step ingestFranceTravailStep,
        Step ingestEmploiNcStep,
        Step ingestRemoteOkStep,
        Step ingestAdzunaStep
    ) {
        return new JobBuilder("ingestJobOffersJob", jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(ingestFranceTravailStep)
            .next(ingestEmploiNcStep)
            .next(ingestRemoteOkStep)
            .next(ingestAdzunaStep)
            .build();
    }

    // --- France Travail: partitioned by ROME grand domaine ---

    @Bean
    public Step ingestFranceTravailStep(JobRepository jobRepository, Step franceTravailWorkerStep, TaskExecutor batchTaskExecutor) {
        return new StepBuilder("ingestFranceTravailStep", jobRepository)
            .partitioner(franceTravailWorkerStep.getName(), new GrandDomainePartitioner(List.of()))
            .step(franceTravailWorkerStep)
            .gridSize(RomeGrandDomaine.ALL.size())
            .taskExecutor(batchTaskExecutor)
            .build();
    }

    @Bean
    public Step franceTravailWorkerStep(JobRepository jobRepository, AtsItemReader franceTravailReader) {
        return new StepBuilder("franceTravailWorkerStep", jobRepository)
            .<JobOffer, JobOffer>chunk(CHUNK_SIZE, transactionManager)
            .reader(franceTravailReader)
            .processor(statelessIngestionProcessor())
            .writer(jobOfferBatchWriter)
            .faultTolerant()
            .skipLimit(SKIP_LIMIT)
            .skip(Exception.class)
            .listener(skipListener)
            .build();
    }

    @Bean
    @StepScope
    public AtsItemReader franceTravailReader(
        FranceTravailConnector connector,
        @Value("#{stepExecutionContext['grandDomaine']}") String grandDomaine,
        @Value("#{stepExecutionContext['departement']}") String departement
    ) {
        return new AtsItemReader(connector.fetchByGrandDomaine(grandDomaine, departement));
    }

    // --- Emploi-NC ---

    @Bean
    public Step ingestEmploiNcStep(JobRepository jobRepository, AtsItemReader emploiNcReader) {
        return new StepBuilder("ingestEmploiNcStep", jobRepository)
            .<JobOffer, JobOffer>chunk(CHUNK_SIZE, transactionManager)
            .reader(emploiNcReader)
            .processor(statelessIngestionProcessor())
            .writer(jobOfferBatchWriter)
            .faultTolerant()
            .skipLimit(SKIP_LIMIT)
            .skip(Exception.class)
            .listener(skipListener)
            .build();
    }

    @Bean
    @StepScope
    public AtsItemReader emploiNcReader(EmploiNcConnector connector) {
        // Step-scoped so each run gets a fresh (lazy) reader rather than reusing an exhausted iterator.
        return new AtsItemReader(connector, null, null);
    }

    // --- RemoteOK (international remote tech) ---

    @Bean
    public Step ingestRemoteOkStep(JobRepository jobRepository, AtsItemReader remoteOkReader) {
        return new StepBuilder("ingestRemoteOkStep", jobRepository)
            .<JobOffer, JobOffer>chunk(CHUNK_SIZE, transactionManager)
            .reader(remoteOkReader)
            .processor(statelessIngestionProcessor())
            .writer(jobOfferBatchWriter)
            .faultTolerant()
            .skipLimit(SKIP_LIMIT)
            .skip(Exception.class)
            .listener(skipListener)
            .build();
    }

    @Bean
    @StepScope
    public AtsItemReader remoteOkReader(RemoteOkConnector connector) {
        // Null keywords → the whole RemoteOK feed (it filters by keyword only when provided).
        return new AtsItemReader(connector, null, null);
    }

    // --- Adzuna (aggregated FR / international, seeded by configured queries) ---

    @Bean
    public Step ingestAdzunaStep(JobRepository jobRepository, AtsItemReader adzunaReader) {
        return new StepBuilder("ingestAdzunaStep", jobRepository)
            .<JobOffer, JobOffer>chunk(CHUNK_SIZE, transactionManager)
            .reader(adzunaReader)
            .processor(statelessIngestionProcessor())
            .writer(jobOfferBatchWriter)
            .faultTolerant()
            .skipLimit(SKIP_LIMIT)
            .skip(Exception.class)
            .listener(skipListener)
            .build();
    }

    @Bean
    @StepScope
    public AtsItemReader adzunaReader(AdzunaConnector connector) {
        // Paginates over every configured seed query; empty (no-op) when credentials are missing.
        return new AtsItemReader(connector.fetchConfiguredSeeds());
    }

    // --- Shared components ---

    @Bean
    public ItemProcessor<JobOffer, JobOffer> statelessIngestionProcessor() {
        // Dedup is done per-chunk in the writer, so the processor only enriches (no DB lookup).
        return ingestionService::prepareForIngestionStateless;
    }

    @Bean
    public TaskExecutor batchTaskExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("ft-partition-");
        executor.setVirtualThreads(true);
        executor.setConcurrencyLimit(PARTITION_CONCURRENCY);
        return executor;
    }
}
