package nc.opt.aiassistant.batch;

import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.JobOffer;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.stereotype.Component;

/**
 * Logs the actual cause behind every skipped item. Without this, a fault-tolerant step only reports
 * {@code SkipLimitExceededException} once the limit is hit, hiding the real (often systematic) error.
 */
@Slf4j
@Component
public class BatchSkipListener implements SkipListener<JobOffer, JobOffer> {

    @Override
    public void onSkipInRead(Throwable t) {
        log.error("Skipped item on READ: {}", t.getMessage(), t);
    }

    @Override
    public void onSkipInProcess(JobOffer item, Throwable t) {
        log.error("Skipped offer on PROCESS [{}/{}]: {}", item.getSource(), item.getSourceId(), t.getMessage(), t);
    }

    @Override
    public void onSkipInWrite(JobOffer item, Throwable t) {
        log.error(
            "Skipped offer on WRITE [{}/{}] title='{}': {}",
            item.getSource(),
            item.getSourceId(),
            item.getTitle(),
            t.getMessage(),
            t
        );
    }
}
