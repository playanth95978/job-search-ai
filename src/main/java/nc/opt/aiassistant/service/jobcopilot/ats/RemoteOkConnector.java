package nc.opt.aiassistant.service.jobcopilot.ats;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.domain.enumeration.JobSource;
import nc.opt.aiassistant.service.jobcopilot.ats.support.AtsDates;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Connector for the public RemoteOK feed ({@code https://remoteok.com/api}), remote-only listings.
 *
 * <p>The feed is a bare JSON array whose <b>first element is a legal notice</b>, not a job: entries
 * without a {@code position} are skipped. Keyword filtering is done client-side, the feed having no
 * query parameters.</p>
 *
 * <p>{@code location} is deliberately ignored: every RemoteOK posting is remote, so filtering them by
 * city would empty the source rather than narrow it. The geographic re-ranking done by
 * {@code JobSearchService} on the aggregated results is what places them relative to the search.</p>
 */
@Slf4j
@Component
public final class RemoteOkConnector extends AbstractAtsConnector {

    private static final String API_URL = "https://remoteok.com/api";

    public RemoteOkConnector(@Qualifier("atsRestClient") RestClient restClient, @Qualifier("atsObjectMapper") ObjectMapper objectMapper) {
        super(restClient, objectMapper);
    }

    // --- Payload ------------------------------------------------------------------------------

    record RemoteOkJob(
        String id,
        String position,
        String company,
        String location,
        String description,
        String url,
        String date,
        List<String> tags
    ) {}

    // --- Connector ----------------------------------------------------------------------------

    @Override
    public boolean supports(String url) {
        return StringUtils.contains(url, "remoteok.com");
    }

    @Override
    public JobSource getSource() {
        return JobSource.REMOTEOK;
    }

    @Override
    public List<JobOffer> fetchFromUrl(String url) {
        // A bare remoteok.com URL maps to the whole remote feed.
        return fetchJobs(null, null);
    }

    @Override
    public List<JobOffer> fetchJobs(String keywords, String location) {
        RemoteOkJob[] jobs = getJson(API_URL, RemoteOkJob[].class);
        if (jobs == null) {
            return List.of();
        }
        return java.util.Arrays.stream(jobs)
            .filter(Objects::nonNull)
            .filter(job -> StringUtils.isNotBlank(job.position()))
            .filter(job -> matchesAllKeywords(keywords, job.position(), job.company(), job.description()))
            .map(this::mapJobOffer)
            .toList();
    }

    JobOffer mapJobOffer(RemoteOkJob job) {
        JobOffer offer = new JobOffer();
        offer.setTitle(job.position());
        offer.setCompany(StringUtils.trimToNull(job.company()));
        offer.setLocation(StringUtils.defaultIfBlank(job.location(), "Remote"));
        offer.setRemote(true);
        offer.setDescription(stripHtml(job.description()));
        offer.setApplyUrl(StringUtils.trimToNull(job.url()));
        offer.setSourceId(StringUtils.trimToNull(job.id()));
        offer.setSource(JobSource.REMOTEOK);
        offer.setPublishedAt(AtsDates.parse(job.date()));
        offer.setSkills(skillsJson(job.tags()));
        return offer;
    }

    /** Tags are the offer's skills; stored as the JSON array the rest of the pipeline expects. */
    private String skillsJson(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(tags.stream().filter(StringUtils::isNotBlank).toList());
        } catch (Exception e) {
            log.debug("RemoteOK: unserialisable tags {}", tags);
            return null;
        }
    }
}
