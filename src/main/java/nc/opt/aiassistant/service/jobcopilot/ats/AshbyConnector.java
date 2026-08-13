package nc.opt.aiassistant.service.jobcopilot.ats;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.domain.enumeration.JobSource;
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
@RequiredArgsConstructor
public class AshbyConnector implements AtsConnector {

    private static final String API_BASE = "https://api.ashbyhq.com/posting-api/job-board/";

    /** Posting URL: jobs.ashbyhq.com/{org}/{postingId} (postingId is a UUID). */
    private static final Pattern URL_REF = Pattern.compile("ashbyhq\\.com/([^/?#]+)/([0-9a-fA-F-]{36})");

    private final ObjectMapper objectMapper;

    /** Comma-separated Ashby job-board names crawled by the ingestion; blank = pasted URLs only. */
    @Value("${application.job-copilot.ashby.boards:}")
    private String boardsCsv;

    @Override
    public boolean supports(String url) {
        return url != null && url.contains("ashbyhq.com");
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
        return board.isBlank() ? List.of() : fetchFromBoard(board);
    }

    /** Fetches every listed posting of the configured boards (ingestion entry point). */
    public List<JobOffer> fetchConfiguredBoards() {
        List<String> boards = Arrays.stream(boardsCsv.split(","))
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .toList();
        if (boards.isEmpty()) {
            return List.of();
        }
        List<JobOffer> all = new ArrayList<>();
        boards.forEach(board -> all.addAll(fetchFromBoard(board)));
        return all;
    }

    public List<JobOffer> fetchFromBoard(String boardName) {
        List<JobOffer> results = new ArrayList<>();
        try {
            String body = RestClient.create()
                .get()
                .uri(API_BASE + boardName)
                .retrieve()
                .body(String.class);

            JsonNode root = objectMapper.readTree(body);
            for (JsonNode job : root.path("jobs")) {
                if (!job.path("isListed").asBoolean(true)) {
                    continue;
                }
                results.add(mapJobOffer(job, boardName));
            }
        } catch (Exception e) {
            log.error("Ashby fetch failed for board={}: {}", boardName, e.getMessage());
        }
        return results;
    }

    /** Board name from a hosted board URL (jobs.ashbyhq.com/{org}) or from the API path. */
    private String boardOf(String url) {
        if (url.contains("/job-board/")) {
            return stripQuery(AtsConnector.boardSlug(url, "/job-board/"));
        }
        return stripQuery(AtsConnector.boardSlug(url, "ashbyhq.com/"));
    }

    private static String stripQuery(String value) {
        int cut = value.indexOf('?');
        return cut < 0 ? value : value.substring(0, cut);
    }

    JobOffer mapJobOffer(JsonNode job, String boardName) {
        JobOffer offer = new JobOffer();
        offer.setTitle(job.path("title").asText(""));
        offer.setCompany(boardName);
        offer.setLocation(blankToNull(job.path("location").asText(null)));
        offer.setCountry(blankToNull(job.path("address").path("postalAddress").path("addressCountry").asText(null)));
        // descriptionPlain is provided alongside descriptionHtml: no HTML stripping needed.
        offer.setDescription(firstNonBlank(job.path("descriptionPlain").asText(null), job.path("descriptionHtml").asText(null)));
        offer.setApplyUrl(firstNonBlank(job.path("applyUrl").asText(null), job.path("jobUrl").asText(null)));
        offer.setSourceId(firstNonBlank(job.path("id").asText(null), offer.getApplyUrl()));
        offer.setSource(JobSource.ASHBY);
        offer.setContractType(blankToNull(job.path("employmentType").asText(null)));
        // isRemote is authoritative; workplaceType adds Hybrid, which is not "remote".
        offer.setRemote(job.path("isRemote").asBoolean(false) || "Remote".equals(job.path("workplaceType").asText(null)));
        offer.setPublishedAt(parseInstant(job.path("publishedAt").asText(null)));
        return offer;
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
