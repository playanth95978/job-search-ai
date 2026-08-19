package nc.opt.aiassistant.service.jobcopilot;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.repository.jobcopilot.JobOfferRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fills {@code job_offer.category} on the offers ingested before the column existed.
 *
 * <p>Runs in bounded pages rather than one big update: the corpus is mass-ingested and can hold tens of
 * thousands of rows, and the classification is Java-side (accent folding, ordered keyword groups), so
 * it cannot be expressed as a single SQL statement without duplicating the rules in the changelog —
 * where they would silently drift from {@link JobCategoryResolver}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobCategoryBackfillService {

    private static final int PAGE_SIZE = 500;

    private final JobOfferRepository jobOfferRepository;
    private final JobCategoryResolver resolver;

    /**
     * Classifies every offer that has no category yet.
     *
     * @return how many offers were updated.
     */
    public int backfill() {
        int updated = 0;
        List<JobOffer> page;
        // Always page 0: each pass sets `category`, so the classified rows drop out of the query.
        while (!(page = jobOfferRepository.findByCategoryIsNull(PageRequest.of(0, PAGE_SIZE))).isEmpty()) {
            updated += classifyPage(page);
            log.info("Category backfill: {} offers classified so far", updated);
        }
        log.info("Category backfill finished: {} offers classified", updated);
        return updated;
    }

    @Transactional
    protected int classifyPage(List<JobOffer> offers) {
        // Persisted rows carry no source label any more (it is transient), so the backfill is
        // title-based by construction; new ingestions get the better, source-stated family.
        offers.forEach(offer -> offer.setCategory(resolver.resolve(offer)));
        jobOfferRepository.saveAll(offers);
        return offers.size();
    }
}
