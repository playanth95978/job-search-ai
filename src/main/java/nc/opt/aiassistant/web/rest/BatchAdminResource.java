package nc.opt.aiassistant.web.rest;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.PaginationUtil;

/**
 * Admin endpoints for the Spring Batch ingestion dashboard: list configured jobs, launch a run, and
 * browse past executions (status, start/end, per-step read/write/skip counts).
 *
 * <p>Secured by {@code /api/admin/**} → ADMIN in the security configuration.</p>
 */
@RestController
@RequestMapping("/api/admin/batch")
@Slf4j
public class BatchAdminResource {

    /** Jobs are launched off-thread so the dashboard returns immediately. */
    private final ExecutorService launchExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private final JobOperator jobOperator;
    private final JobRepository jobRepository;
    private final Map<String, Job> jobs;

    public BatchAdminResource(JobOperator jobOperator, JobRepository jobRepository, Map<String, Job> jobs) {
        this.jobOperator = jobOperator;
        this.jobRepository = jobRepository;
        this.jobs = jobs;
    }

    /** Configured job names (bean name == batch job name), sorted. */
    @GetMapping("/jobs")
    public List<JobSummaryDTO> getJobs() {
        return jobs
            .values()
            .stream()
            .map(Job::getName)
            .distinct()
            .sorted()
            .map(name -> {
                List<JobInstance> instances = jobRepository.getJobInstances(name, 0, 1);
                JobExecution last = instances.isEmpty() ? null : jobRepository.getLastJobExecution(instances.getFirst());
                long running = jobRepository
                    .getJobInstances(name, 0, 100)
                    .stream()
                    .flatMap(i -> jobRepository.getJobExecutions(i).stream())
                    .filter(e -> e.isRunning())
                    .count();
                return new JobSummaryDTO(
                    name,
                    last == null ? null : last.getStatus().toString(),
                    last == null ? null : last.getStartTime(),
                    running
                );
            })
            .toList();
    }

    /** Recent executions for a job, most recent first. */
    @GetMapping("/jobs/{jobName}/executions")
    public ResponseEntity<List<JobExecutionDTO>> getExecutions(@PathVariable String jobName, Pageable pageable) {
        requireJob(jobName);
        long totalInstances;
        try {
            totalInstances = jobRepository.getJobInstanceCount(jobName);
        } catch (org.springframework.batch.core.launch.NoSuchJobException e) {
            totalInstances = 0;
        }
        List<JobInstance> instances = jobRepository.getJobInstances(jobName, (int) pageable.getOffset(), pageable.getPageSize());
        List<JobExecutionDTO> executions = instances
            .stream()
            .flatMap(instance -> jobRepository.getJobExecutions(instance).stream())
            .sorted(Comparator.comparing(JobExecution::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())))
            .map(BatchAdminResource::toDto)
            .toList();

        // Wrap the page content in a Page so PaginationUtil can emit X-Total-Count and Link headers.
        // Total is the instance count (the paginated unit here); executions is this page's flattened content.
        Page<JobExecutionDTO> page = new PageImpl<>(executions, pageable, totalInstances);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);

        return ResponseEntity.ok().headers(headers).body(executions);
    }

    /** Launches a fresh run asynchronously; returns 202 with the job name. */
    @PostMapping("/jobs/{jobName}/execute")
    public ResponseEntity<Void> execute(@PathVariable String jobName) {
        Job job = requireJob(jobName);
        JobParameters params = new JobParametersBuilder().addLong("startAt", System.currentTimeMillis()).toJobParameters();
        log.info("REST request to launch batch job {}", jobName);
        launchExecutor.submit(() -> {
            try {
                jobOperator.start(job, params);
            } catch (Exception e) {
                log.error("Failed to launch batch job {}: {}", jobName, e.getMessage(), e);
            }
        });
        return ResponseEntity.accepted().build();
    }

    private Job requireJob(String jobName) {
        return jobs
            .values()
            .stream()
            .filter(j -> j.getName().equals(jobName))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown job: " + jobName));
    }

    private static JobExecutionDTO toDto(JobExecution e) {
        Long durationMs = (e.getStartTime() != null && e.getEndTime() != null)
            ? Duration.between(e.getStartTime(), e.getEndTime()).toMillis()
            : null;
        List<StepExecutionDTO> steps = e
            .getStepExecutions()
            .stream()
            .sorted(Comparator.comparing(StepExecution::getStepName))
            .map(s ->
                new StepExecutionDTO(
                    s.getStepName(),
                    s.getStatus().toString(),
                    s.getReadCount(),
                    s.getWriteCount(),
                    s.getSkipCount(),
                    s.getCommitCount()
                )
            )
            .toList();
        return new JobExecutionDTO(
            e.getId(),
            e.getJobInstance() == null ? null : e.getJobInstance().getInstanceId(),
            e.getJobInstance() == null ? null : e.getJobInstance().getJobName(),
            e.getStatus().toString(),
            e.getExitStatus() == null ? null : e.getExitStatus().getExitCode(),
            e.getExitStatus() == null ? null : e.getExitStatus().getExitDescription(),
            e.getCreateTime(),
            e.getStartTime(),
            e.getEndTime(),
            durationMs,
            steps
        );
    }

    public record JobSummaryDTO(String name, String lastStatus, LocalDateTime lastStartTime, long runningCount) {}

    public record JobExecutionDTO(
        Long executionId,
        Long instanceId,
        String jobName,
        String status,
        String exitCode,
        String exitDescription,
        LocalDateTime createTime,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Long durationMs,
        List<StepExecutionDTO> steps
    ) {}

    public record StepExecutionDTO(String stepName, String status, long readCount, long writeCount, long skipCount, long commitCount) {}
}
