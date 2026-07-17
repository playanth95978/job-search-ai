package nc.opt.aiassistant.batch;

import java.time.LocalDateTime;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * On startup, marks job/step executions left in a running state (STARTED/STARTING) as ABANDONED.
 *
 * <p>Spring Batch persists a JobExecution as STARTED and only flips it on completion. If the JVM is
 * killed mid-run (crash, {@code Ctrl-C}, container restart), those rows stay "running" forever:
 * {@code JobExecution.isRunning()} keeps returning true, so the admin dashboard reports a non-zero
 * {@code runningCount} and polls endlessly. A fresh boot has no genuinely-running executions, so any
 * still marked running are stale and can be safely abandoned.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StaleJobExecutionCleaner {

    private final JobRepository jobRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void abandonStaleExecutions() {
        LocalDateTime now = LocalDateTime.now();
        int abandoned = 0;
        for (String jobName : jobRepository.getJobNames()) {
            Set<JobExecution> running = jobRepository.findRunningJobExecutions(jobName);
            for (JobExecution execution : running) {
                for (StepExecution step : execution.getStepExecutions()) {
                    if (step.getStatus().isRunning()) {
                        step.setStatus(BatchStatus.ABANDONED);
                        step.setExitStatus(ExitStatus.STOPPED);
                        if (step.getEndTime() == null) step.setEndTime(now);
                        jobRepository.update(step);
                    }
                }
                execution.setStatus(BatchStatus.ABANDONED);
                execution.setExitStatus(ExitStatus.STOPPED);
                if (execution.getEndTime() == null) execution.setEndTime(now);
                jobRepository.update(execution);
                abandoned++;
            }
        }
        if (abandoned > 0) {
            log.warn("Abandoned {} stale job execution(s) left running from a previous shutdown", abandoned);
        }
    }
}
