package nc.opt.aiassistant.service.jobcopilot.ats;

import java.util.List;
import nc.opt.aiassistant.domain.CandidateProfile;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.domain.enumeration.JobSource;

public interface AtsConnector {
    boolean supports(String url);

    JobSource getSource();

    List<JobOffer> fetchJobs(String keywords, String location);

    /**
     * Fetches the full offer (notably its complete description) from its detail page.
     * Used to enrich a lightweight list result lazily, one request at a time.
     *
     * @return the enriched offer, or {@code null} if this connector cannot enrich the URL.
     */
    default JobOffer fetchDetail(String url) {
        return null;
    }

    /** Whether this connector can submit an application directly via an ATS API. */
    default boolean supportsAutoApply() {
        return false;
    }

    /** Submits the application to the ATS. Only called when {@link #supportsAutoApply()} is true. */
    default ApplyResult applyTo(JobOffer offer, CandidateProfile profile, String coverLetter) {
        throw new UnsupportedOperationException("Auto-apply not supported for " + getSource());
    }

    record ApplyResult(boolean success, String message) {}
}
