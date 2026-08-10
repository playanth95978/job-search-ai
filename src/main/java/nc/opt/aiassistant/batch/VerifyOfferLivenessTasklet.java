package nc.opt.aiassistant.batch;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.repository.jobcopilot.JobOfferRepository;
import nc.opt.aiassistant.service.jobcopilot.OfferLivenessChecker;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

/**
 * Verifies, batch by batch, that the apply URLs of a partition's offers are still reachable.
 *
 * <p>Reads the least-recently-checked active offers, HTTP-probes each URL and — only for a hard
 * 404/410 — marks the offer expired ({@code expires_at = now}) so the existing purge removes it.
 * Every probed offer is stamped {@code last_checked_at = now} (whatever the outcome) so it rotates
 * to the back and the tasklet always advances; it returns {@link RepeatStatus#CONTINUABLE} until the
 * partition is exhausted or the per-run cap is reached.</p>
 */
@Slf4j
public class VerifyOfferLivenessTasklet implements Tasklet {

    private final JobOfferRepository jobOfferRepository;
    private final OfferLivenessChecker livenessChecker;
    private final int partitionIndex;
    private final int gridSize;
    private final int batchSize;
    private final int recheckHours;
    private final int maxPerRun;

    private int processed = 0;

    public VerifyOfferLivenessTasklet(
        JobOfferRepository jobOfferRepository,
        OfferLivenessChecker livenessChecker,
        int partitionIndex,
        int gridSize,
        int batchSize,
        int recheckHours,
        int maxPerRun
    ) {
        this.jobOfferRepository = jobOfferRepository;
        this.livenessChecker = livenessChecker;
        this.partitionIndex = partitionIndex;
        this.gridSize = gridSize;
        this.batchSize = batchSize;
        this.recheckHours = recheckHours;
        this.maxPerRun = maxPerRun;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        if (processed >= maxPerRun) {
            return RepeatStatus.FINISHED;
        }

        Instant now = Instant.now();
        Instant staleBefore = now.minus(recheckHours, ChronoUnit.HOURS);
        int limit = Math.min(batchSize, maxPerRun - processed);

        List<Object[]> rows = jobOfferRepository.findLivenessCandidates(now, staleBefore, gridSize, partitionIndex, limit);
        if (rows.isEmpty()) {
            return RepeatStatus.FINISHED;
        }

        List<UUID> checked = new ArrayList<>(rows.size());
        List<UUID> dead = new ArrayList<>();
        for (Object[] row : rows) {
            UUID id = (UUID) row[0];
            String applyUrl = (String) row[1];
            checked.add(id);
            if (livenessChecker.check(applyUrl) == OfferLivenessChecker.Liveness.DEAD) {
                dead.add(id);
            }
        }

        if (!dead.isEmpty()) {
            jobOfferRepository.markExpiredByIds(dead, now);
        }
        jobOfferRepository.markCheckedByIds(checked, now);

        processed += rows.size();
        contribution.incrementWriteCount(dead.size());
        log.debug("Liveness partition {}/{} : {} vérifiée(s), {} morte(s)", partitionIndex, gridSize, rows.size(), dead.size());

        return rows.size() < limit ? RepeatStatus.FINISHED : RepeatStatus.CONTINUABLE;
    }
}
