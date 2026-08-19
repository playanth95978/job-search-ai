package nc.opt.aiassistant.service.jobcopilot;

import lombok.RequiredArgsConstructor;
import nc.opt.aiassistant.domain.JobOffer;
import org.springframework.stereotype.Component;

/**
 * Decides the family stamped on {@code job_offer.category} at ingestion, in order of trustworthiness:
 *
 * <ol>
 *   <li>the source's own taxonomy, mapped by {@link SourceCategoryMapper} — curated by the board itself;
 *   <li>failing that, {@link JobCategoryClassifier} on the title — a guess, for the boards that publish
 *       no taxonomy (Emploi.nc, the ATS boards, Seek);
 *   <li>{@link JobCategories#OTHER} when even that finds nothing.
 * </ol>
 *
 * The order matters: title keywords mislabel often enough ("Chargé de mission" is anything at all) that
 * a stated ROME code must always win over them.
 */
@Component
@RequiredArgsConstructor
public class JobCategoryResolver {

    private final SourceCategoryMapper sourceCategoryMapper;
    private final JobCategoryClassifier classifier;

    public String resolve(JobOffer offer) {
        if (offer == null) {
            return JobCategories.OTHER;
        }
        return sourceCategoryMapper.map(offer.getSource(), offer.getSourceCategory()).orElseGet(() -> classifier.classify(offer));
    }
}
