package nc.opt.aiassistant.service.jobcopilot.ats;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.domain.enumeration.JobSource;
import nc.opt.aiassistant.service.jobcopilot.ats.support.AtsDates;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Connector for the <a href="https://developers.ashbyhq.com/docs/public-job-posting-api">Ashby public
 * Job Posting API</a>: {@code GET https://api.ashbyhq.com/posting-api/job-board/{boardName}}, no
 * authentication. {@code includeCompensation=true} adds a compensation block, which Ashby exposes as
 * free-text summaries (tiers, ranges) rather than numbers — so it is kept out of the salary columns.
 *
 * <p>Only postings flagged {@code isListed} are kept: an unlisted posting is one the company chose not
 * to publish on its board.</p>
 */
@Slf4j
@Component
public final class AshbyConnector extends AbstractAtsConnector {

    private static final String API_BASE = "https://api.ashbyhq.com/posting-api/job-board/";

    /** Posting URL: jobs.ashbyhq.com/{org}/{postingId} (postingId is a UUID). */
    private static final Pattern URL_REF = Pattern.compile("ashbyhq\\.com/([^/?#]+)/([0-9a-fA-F-]{36})");

    /** {@code workplaceType} also carries "Hybrid", which is not remote. */
    private static final String REMOTE_WORKPLACE = "Remote";

    /** Comma-separated Ashby job-board names crawled by the ingestion; blank = pasted URLs only. */
    @Value("${application.job-copilot.ashby.boards:}")
    private String boardsCsv;

    public AshbyConnector(@Qualifier("atsRestClient") RestClient restClient, @Qualifier("atsObjectMapper") ObjectMapper objectMapper) {
        super(restClient, objectMapper);
    }

    // --- Payload ------------------------------------------------------------------------------

    record AshbyBoard(List<AshbyJob> jobs) {}

    record AshbyJob(
        String id,
        String title,
        String location,
        Boolean isListed,
        Boolean isRemote,
        String workplaceType,
        String employmentType,
        String descriptionHtml,
        String descriptionPlain,
        String publishedAt,
        AshbyAddress address,
        String jobUrl,
        String applyUrl
    ) {}

    record AshbyAddress(AshbyPostalAddress postalAddress) {}

    record AshbyPostalAddress(String addressCountry) {}

    // --- Connector ----------------------------------------------------------------------------

    @Override
    public boolean supports(String url) {
        return StringUtils.contains(url, "ashbyhq.com");
    }

    @Override
    public JobSource getSource() {
        return JobSource.ASHBY;
    }

    @Override
    public PostingRef parseRef(String url) {
        if (url == null) {
            return null;
        }
        Matcher m = URL_REF.matcher(url);
        return m.find() ? new PostingRef(m.group(1), m.group(2)) : null;
    }

    /** Board-based ATS: no cross-company search endpoint, so keyword search is a no-op. */
    @Override
    public List<JobOffer> fetchJobs(String keywords, String location) {
        return List.of();
    }

    @Override
    public List<JobOffer> fetchFromUrl(String url) {
        String board = boardOf(url);
        return StringUtils.isBlank(board) ? List.of() : fetchFromBoard(board);
    }

    /** Fetches every listed posting of the configured boards (ingestion entry point). */
    public List<JobOffer> fetchConfiguredBoards() {
        List<JobOffer> all = new ArrayList<>();
        csv(boardsCsv).forEach(board -> all.addAll(fetchFromBoard(board)));
        return all;
    }

    public List<JobOffer> fetchFromBoard(String boardName) {
        AshbyBoard board = getJson(API_BASE + boardName, AshbyBoard.class);
        if (board == null || board.jobs() == null) {
            return List.of();
        }
        return board
            .jobs()
            .stream()
            .filter(Objects::nonNull)
            .filter(job -> BooleanUtils.isNotFalse(job.isListed()))
            .map(job -> mapJobOffer(job, boardName))
            .toList();
    }

    /** Board name from a hosted board URL (jobs.ashbyhq.com/{org}) or from the API path. */
    private String boardOf(String url) {
        return StringUtils.contains(url, "/job-board/") ? slugAfter(url, "/job-board/") : slugAfter(url, "ashbyhq.com/");
    }

    JobOffer mapJobOffer(AshbyJob job, String boardName) {
        JobOffer offer = new JobOffer();
        offer.setTitle(StringUtils.defaultString(job.title()));
        offer.setCompany(boardName);
        offer.setLocation(StringUtils.trimToNull(job.location()));
        offer.setCountry(countryOf(job));
        // descriptionPlain is provided alongside descriptionHtml: no HTML stripping needed.
        offer.setDescription(StringUtils.firstNonBlank(job.descriptionPlain(), stripHtml(job.descriptionHtml())));
        offer.setApplyUrl(StringUtils.firstNonBlank(job.applyUrl(), job.jobUrl()));
        offer.setSourceId(StringUtils.firstNonBlank(job.id(), offer.getApplyUrl()));
        offer.setSource(JobSource.ASHBY);
        offer.setContractType(StringUtils.trimToNull(job.employmentType()));
        // isRemote is authoritative; workplaceType adds Hybrid, which is not "remote".
        offer.setRemote(BooleanUtils.isTrue(job.isRemote()) || REMOTE_WORKPLACE.equals(job.workplaceType()));
        offer.setPublishedAt(AtsDates.parse(job.publishedAt()));
        return offer;
    }

    private String countryOf(AshbyJob job) {
        if (job.address() == null || job.address().postalAddress() == null) {
            return null;
        }
        return StringUtils.trimToNull(job.address().postalAddress().addressCountry());
    }
}
