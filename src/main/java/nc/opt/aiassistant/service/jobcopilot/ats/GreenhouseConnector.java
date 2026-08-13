package nc.opt.aiassistant.service.jobcopilot.ats;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.domain.enumeration.JobSource;
import nc.opt.aiassistant.service.jobcopilot.ats.support.AtsDates;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Connector for the Greenhouse Job Board API: {@code GET /v1/boards/{board}/jobs?content=true},
 * public, one board per company.
 *
 * <p>Auto-apply is intentionally NOT enabled: submitting an application
 * ({@code POST /v1/boards/{board}/jobs/{id}}) requires the <em>company's</em> Job Board API key,
 * which is per-employer and not publicly available.</p>
 */
@Slf4j
@Component
public final class GreenhouseConnector extends AbstractAtsConnector {

    private static final String API_BASE = "https://boards-api.greenhouse.io/v1/boards";
    /** Greenhouse posting URL: (job-)boards.greenhouse.io/{board}/jobs/{id}, or ?gh_jid={id}. */
    private static final Pattern URL_REF = Pattern.compile("greenhouse\\.io/([^/]+)/jobs/(\\d+)");
    private static final Pattern GH_JID = Pattern.compile("[?&]gh_jid=(\\d+)");

    public GreenhouseConnector(@Qualifier("atsRestClient") RestClient restClient, @Qualifier("atsObjectMapper") ObjectMapper objectMapper) {
        super(restClient, objectMapper);
    }

    // --- Payload ------------------------------------------------------------------------------

    record GreenhouseBoard(List<GreenhouseJob> jobs) {}

    record GreenhouseJob(
        Long id,
        String title,
        String content,
        GreenhouseLocation location,
        @JsonProperty("absolute_url") String absoluteUrl,
        @JsonProperty("updated_at") String updatedAt,
        @JsonProperty("first_published") String firstPublished
    ) {}

    record GreenhouseLocation(String name) {}

    // --- Connector ----------------------------------------------------------------------------

    @Override
    public boolean supports(String url) {
        return StringUtils.contains(url, "greenhouse.io");
    }

    @Override
    public JobSource getSource() {
        return JobSource.GREENHOUSE;
    }

    @Override
    public PostingRef parseRef(String url) {
        if (url == null) {
            return null;
        }
        Matcher m = URL_REF.matcher(url);
        if (m.find()) {
            return new PostingRef(m.group(1), m.group(2));
        }
        Matcher j = GH_JID.matcher(url);
        return j.find() ? new PostingRef(null, j.group(1)) : null;
    }

    @Override
    public List<JobOffer> fetchJobs(String keywords, String location) {
        return List.of();
    }

    /** Fetches jobs from a specific Greenhouse board (company slug). */
    @Override
    public List<JobOffer> fetchFromUrl(String url) {
        String board = slugAfter(url, "greenhouse.io/");
        return StringUtils.isBlank(board) ? List.of() : fetchFromBoard(board);
    }

    public List<JobOffer> fetchFromBoard(String boardToken) {
        GreenhouseBoard board = getJson(API_BASE + "/" + boardToken + "/jobs?content=true", GreenhouseBoard.class);
        if (board == null || board.jobs() == null) {
            return List.of();
        }
        return board
            .jobs()
            .stream()
            .filter(Objects::nonNull)
            .map(job -> mapJobOffer(job, boardToken))
            .toList();
    }

    JobOffer mapJobOffer(GreenhouseJob job, String boardToken) {
        JobOffer offer = new JobOffer();
        offer.setTitle(StringUtils.defaultString(job.title()));
        offer.setCompany(boardToken);
        offer.setLocation(job.location() == null ? null : StringUtils.trimToNull(job.location().name()));
        // `content` is HTML-escaped markup; Jsoup unescapes and flattens it in one pass.
        offer.setDescription(stripHtml(job.content()));
        offer.setApplyUrl(StringUtils.trimToNull(job.absoluteUrl()));
        offer.setSourceId(job.id() == null ? null : String.valueOf(job.id()));
        offer.setSource(JobSource.GREENHOUSE);
        offer.setPublishedAt(AtsDates.parse(StringUtils.firstNonBlank(job.firstPublished(), job.updatedAt())));
        offer.setRemote(detectRemote(offer.getLocation(), offer.getTitle()));
        return offer;
    }
}
