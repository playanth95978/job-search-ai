package nc.opt.aiassistant.batch;

import java.util.ArrayList;
import java.util.Map;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.domain.enumeration.EmbeddingStatus;
import nc.opt.aiassistant.repository.jobcopilot.JobOfferRepository;
import nc.opt.aiassistant.service.jobcopilot.JobOfferIngestionService;
import nc.opt.aiassistant.service.jobcopilot.JobOfferProcessingService;
import nc.opt.aiassistant.service.jobcopilot.ats.EmploiNcConnector;
import nc.opt.aiassistant.service.jobcopilot.ats.FranceTravailConnector;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.EnableJdbcJobRepository;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.data.RepositoryItemReader;
import org.springframework.batch.infrastructure.item.data.RepositoryItemWriter;
import org.springframework.batch.infrastructure.item.data.builder.RepositoryItemReaderBuilder;
import org.springframework.batch.infrastructure.item.data.builder.RepositoryItemWriterBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@EnableBatchProcessing
@EnableJdbcJobRepository
public class JobOfferBatchConfig {

    private final JobOfferIngestionService ingestionService;
    private final JobOfferRepository jobOfferRepository;
    private final JobOfferProcessingService jobOfferProcessingService;
    private final PlatformTransactionManager transactionManager;

    public JobOfferBatchConfig(
        PlatformTransactionManager transactionManager,
        JobOfferIngestionService ingestionService,
        JobOfferRepository jobOfferRepository,
        JobOfferProcessingService jobOfferProcessingService
    ) {
        this.transactionManager = transactionManager;
        this.ingestionService = ingestionService;
        this.jobOfferRepository = jobOfferRepository;
        this.jobOfferProcessingService = jobOfferProcessingService;
    }

    @Bean
    public Job ingestJobOffersJob(
        JobRepository jobRepository,
        Step ingestFranceTravailStep,
        Step ingestEmploiNcStep,
        Step processEmbeddingsStep
    ) {
        return new JobBuilder("ingestJobOffersJob", jobRepository)
            .start(ingestFranceTravailStep)
            .next(ingestEmploiNcStep)
            .next(processEmbeddingsStep)
            .build();
    }

    @Bean
    public Step ingestFranceTravailStep(JobRepository jobRepository, FranceTravailConnector connector) {
        return new StepBuilder("ingestFranceTravailStep", jobRepository)
            .<JobOffer, JobOffer>chunk(5, transactionManager)
            .reader(new AtsItemReader(connector, "informatique", "Nouvelle-Calédonie"))
            .processor(ingestionProcessor())
            .writer(jobOfferWriter())
            .build();
    }

    @Bean
    public Step ingestEmploiNcStep(JobRepository jobRepository, EmploiNcConnector connector) {
        return new StepBuilder("ingestEmploiNcStep", jobRepository)
            .<JobOffer, JobOffer>chunk(50, transactionManager)
            .reader(new AtsItemReader(connector, null, null))
            .processor(ingestionProcessor())
            .writer(jobOfferWriter())
            .build();
    }

    @Bean
    public Step processEmbeddingsStep(JobRepository jobRepository) {
        return new StepBuilder("processEmbeddingsStep", jobRepository)
            .<JobOffer, JobOffer>chunk(50, transactionManager)
            .reader(pendingEmbeddingReader())
            .writer(embeddingWriter())
            .build();
    }

    @Bean
    public ItemProcessor<JobOffer, JobOffer> ingestionProcessor() {
        return ingestionService::prepareForIngestion;
    }

    @Bean
    public RepositoryItemWriter<JobOffer> jobOfferWriter() {
        return new RepositoryItemWriterBuilder<JobOffer>().repository(jobOfferRepository).methodName("save").build();
    }

    @Bean
    public RepositoryItemReader<JobOffer> pendingEmbeddingReader() {
        return new RepositoryItemReaderBuilder<JobOffer>()
            .name("pendingEmbeddingReader")
            .repository(jobOfferRepository)
            .methodName("findByEmbeddingStatus")
            .arguments(EmbeddingStatus.PENDING)
            .pageSize(50)
            .sorts(Map.of("createdAt", Sort.Direction.ASC))
            .build();
    }

    @Bean
    public ItemWriter<JobOffer> embeddingWriter() {
        return chunk -> jobOfferProcessingService.processBatch(new ArrayList<>(chunk.getItems()));
    }
}
