package nc.opt.aiassistant.service.jobcopilot.ats;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.domain.enumeration.JobSource;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Connector for <a href="https://help.workable.com/hc/en-us/articles/115012771647">Workable's public
 * careers-page API</a>: {@code GET https://www.workable.com/api/accounts/{account}?details=true},
 * no API key, published jobs only.
 *
 * <p>That documented URL 302-redirects to the widget host, which is what we call directly here — the
 * default {@code RestClient} does not follow cross-host redirects, and both serve the same payload.
 * The authenticated {@code /spi/v3/jobs} endpoint is deliberately not used: it needs the employer's
 * own token and returns drafts/closed postings.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkableConnector implements AtsConnector {

    private static final String API_BASE = "https://apply.workable.com/api/v1/widget/accounts/";

    /** Posting URLs: apply.workable.com/j/{SHORTCODE} or apply.workable.com/{account}/j/{SHORTCODE}. */
    private static final Pattern ACCOUNT_POSTING = Pattern.compile("apply\\.workable\\.com/([^/]+)/j/([A-Za-z0-9]+)");
    private static final Pattern DIRECT_POSTING = Pattern.compile("apply\\.workable\\.com/j/([A-Za-z0-9]+)");

    private final ObjectMapper objectMapper;

    /** Comma-separated Workable account slugs crawled by the ingestion; blank = pasted URLs only. */
    @Value("${application.job-copilot.workable.accounts:}")
    private String accountsCsv;

    @Override
    public boolean supports(String url) {
        return url != null && url.contains("workable.com");
    }

    @Override
    public JobSource getSource() {
        return JobSource.WORKABLE;
    }

    @Override
    public PostingRef parseRef(String url) {
        if (url == null) {
            return null;
        }
        Matcher withAccount = ACCOUNT_POSTING.matcher(url);
        if (withAccount.find() && !"j".equals(withAccount.group(1))) {
            return new PostingRef(withAccount.group(1), withAccount.group(2));
        }
        Matcher direct = DIRECT_POSTING.matcher(url);
        return direct.find() ? new PostingRef(null, direct.group(1)) : null;
    }

    /** Board-based ATS: there is no cross-company search endpoint, so keyword search is a no-op. */
    @Override
    public List<JobOffer> fetchJobs(String keywords, String location) {
        return List.of();
    }

    @Override
    public List<JobOffer> fetchFromUrl(String url) {
        String account = accountOf(url);
        return account.isBlank() ? List.of() : fetchFromAccount(account);
    }

    /** Fetches every published job of the configured accounts (ingestion entry point). */
    public List<JobOffer> fetchConfiguredAccounts() {
        List<String> accounts = Arrays.stream(accountsCsv.split(","))
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .toList();
        if (accounts.isEmpty()) {
            return List.of();
        }
        List<JobOffer> all = new ArrayList<>();
        accounts.forEach(account -> all.addAll(fetchFromAccount(account)));
        return all;
    }

    public List<JobOffer> fetchFromAccount(String account) {
        List<JobOffer> results = new ArrayList<>();
        try {
            String body = RestClient.create()
                .get()
                .uri(API_BASE + account + "?details=true")
                .retrieve()
                .body(String.class);

            JsonNode root = objectMapper.readTree(body);
            String companyName = root.path("name").asText(account);
            for (JsonNode job : root.path("jobs")) {
                results.add(mapJobOffer(job, companyName));
            }
        } catch (Exception e) {
            log.error("Workable fetch failed for account={}: {}", account, e.getMessage());
        }
        return results;
    }

    /**
     * Extracts the account slug from any Workable URL: the careers host ({@code acme.workable.com}),
     * the apply host ({@code apply.workable.com/acme/...}) or the API path itself.
     */
    private String accountOf(String url) {
        Matcher posting = ACCOUNT_POSTING.matcher(url);
        if (posting.find() && !"j".equals(posting.group(1))) {
            return posting.group(1);
        }
        if (url.contains("/accounts/")) {
            return stripQuery(AtsConnector.boardSlug(url, "/accounts/"));
        }
        if (url.contains("apply.workable.com/")) {
            String slug = stripQuery(AtsConnector.boardSlug(url, "apply.workable.com/"));
            return "j".equals(slug) ? "" : slug;
        }
        Matcher subdomain = Pattern.compile("https?://([^./]+)\\.workable\\.com").matcher(url);
        return subdomain.find() && !"apply".equals(subdomain.group(1)) && !"www".equals(subdomain.group(1)) ? subdomain.group(1) : "";
    }

    private static String stripQuery(String value) {
        int q = value.indexOf('?');
        return q < 0 ? value : value.substring(0, q);
    }

    JobOffer mapJobOffer(JsonNode job, String companyName) {
        JobOffer offer = new JobOffer();
        offer.setTitle(job.path("title").asText(""));
        offer.setCompany(companyName);
        offer.setLocation(buildLocation(job));
        offer.setCountry(countryCode(job));
        offer.setDescription(stripHtml(job.path("description").asText("")));
        // application_url is the form itself; url/shortlink are the posting page.
        offer.setApplyUrl(
            firstNonBlank(job.path("application_url").asText(null), job.path("url").asText(null), job.path("shortlink").asText(null))
        );
        offer.setSourceId(blankToNull(job.path("shortcode").asText(null)));
        offer.setSource(JobSource.WORKABLE);
        offer.setContractType(blankToNull(job.path("employment_type").asText(null)));
        offer.setExperienceLevel(blankToNull(job.path("experience").asText(null)));
        offer.setRemote(job.path("telecommuting").asBoolean(false));
        offer.setPublishedAt(parseDate(firstNonBlank(job.path("published_on").asText(null), job.path("created_at").asText(null))));
        return offer;
    }

    /** The flat city/state/country fields mirror the first entry of {@code locations}. */
    private String buildLocation(JsonNode job) {
        List<String> parts = new ArrayList<>();
        addIfPresent(parts, job.path("city").asText(null));
        addIfPresent(parts, job.path("country").asText(null));
        if (parts.isEmpty()) {
            JsonNode first = job.path("locations").path(0);
            addIfPresent(parts, first.path("city").asText(null));
            addIfPresent(parts, first.path("country").asText(null));
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private String countryCode(JsonNode job) {
        return blankToNull(job.path("locations").path(0).path("countryCode").asText(null));
    }

    private static void addIfPresent(List<String> parts, String value) {
        if (value != null && !value.isBlank()) {
            parts.add(value);
        }
    }

    private Instant parseDate(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        try {
            // published_on / created_at are plain dates (2026-07-30).
            return LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (Exception e) {
            try {
                return Instant.parse(date);
            } catch (Exception ignored) {
                return null;
            }
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

    private String stripHtml(String html) {
        return html == null || html.isBlank() ? "" : Jsoup.parse(html).text();
    }
}
