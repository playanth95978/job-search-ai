package nc.opt.aiassistant.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobOfferBatchScheduler {

    private final JobOperator jobLauncher;
    private final Job ingestJobOffersJob;

    /**
     * Runs the job once a day at 2 AM.
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void runIngestionJob() {
        try {
            log.info("Starting scheduled job offer ingestion");
            JobParameters params = new JobParametersBuilder().addLong("startAt", System.currentTimeMillis()).toJobParameters();
            jobLauncher.run(ingestJobOffersJob, params);
        } catch (Exception e) {
            log.error("Failed to run ingestion job: {}", e.getMessage());
        }
    }
}
