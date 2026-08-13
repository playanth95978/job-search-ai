package nc.opt.aiassistant.service.jobcopilot.ats;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
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
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Connector for the <a href="https://jobicy.com/jobs-rss-feed">Jobicy remote-jobs API</a>:
 * {@code GET https://jobicy.com/api/v2/remote-jobs}, public, no key. Remote-only worldwide listings,
 * with structured {@code salaryMin/Max/Currency/Period} and a {@code jobLevel} — which is why it was
 * picked over Remotive (free-text salary, 4 calls/day) and Arbeitnow (no salary, no seniority).
 *
 * <p><b>Rate limit.</b> Jobicy states that polling "must not exceed once per hour". Connectors are
 * queried on every user search, so the whole feed is fetched at most once per {@code cache-ttl} and
 * filtering is then done in memory. One HTTP call per TTL, whatever the search traffic.</p>
 *
 * <p><b>Attribution.</b> Their terms require keeping Jobicy as the source and preserving the canonical
 * job URL: {@code url} is stored as-is in {@code applyUrl}, and the offer carries {@code JOBICY} as
 * its source.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobicyConnector implements AtsConnector {

    private static final String API_BASE = "https://jobicy.com/api/v2/remote-jobs";

    /** Job URL: jobicy.com/jobs/{id}-{slug}. */
    private static final Pattern URL_REF = Pattern.compile("jobicy\\.com/jobs/(\\d+)");

    /** Only yearly amounts land in the salary columns, so they stay comparable across sources. */
    private static final String YEARLY = "yearly";

    /**
     * {@code geo} is the candidate's eligibility region, not a city: only a few slugs are accepted
     * (usa, canada, europe, apac, emea, latam). A searched location is mapped to one when possible,
     * otherwise the filter is dropped rather than guessed.
     */
    private static final Map<String, String> GEO_BY_KEYWORD = Map.ofEntries(
        Map.entry("france", "europe"),
        Map.entry("europe", "europe"),
        Map.entry("uk", "europe"),
        Map.entry("germany", "europe"),
        Map.entry("spain", "europe"),
        Map.entry("australia", "apac"),
        Map.entry("brisbane", "apac"),
        Map.entry("sydney", "apac"),
        Map.entry("melbourne", "apac"),
        Map.entry("new zealand", "apac"),
        Map.entry("asia", "apac"),
        Map.entry("usa", "usa"),
        Map.entry("united states", "usa"),
        Map.entry("canada", "canada"),
        Map.entry("latam", "latam"),
        Map.entry("emea", "emea")
    );

    private final ObjectMapper objectMapper;

    /** 1..100 per the API; 100 is the documented maximum and the default. */
    @Value("${application.job-copilot.jobicy.count:100}")
    private int count;

    /** Minimum interval between two calls to Jobicy — their terms say at most one per hour. */
    @Value("${application.job-copilot.jobicy.cache-ttl:1h}")
    private Duration cacheTtl;

    private final AtomicReference<CachedFeed> cache = new AtomicReference<>();

    private record CachedFeed(Instant fetchedAt, List<JobOffer> offers) {}

    @Override
    public boolean supports(String url) {
        return url != null && url.contains("jobicy.com");
    }

    @Override
    public JobSource getSource() {
        return JobSource.JOBICY;
    }

    @Override
    public PostingRef parseRef(String url) {
        if (url == null) {
            return null;
        }
        Matcher m = URL_REF.matcher(url);
        return m.find() ? new PostingRef(null, m.group(1)) : null;
    }

    /**
     * Serves the cached feed, filtered in memory on the keywords and on the region implied by
     * {@code location}. Jobicy is remote-only: a city-level location never excludes an offer by
     * itself, it only narrows the eligibility region when it maps to one.
     */
    @Override
    public List<JobOffer> fetchJobs(String keywords, String location) {
        List<JobOffer> feed = cachedFeed();
        String geo = geoFor(location);
        return feed
            .stream()
            .filter(offer -> matchesKeywords(offer, keywords))
            .filter(offer -> matchesGeo(offer, geo))
            .toList();
    }

    /** Whole (unfiltered) feed, for the ingestion batch. */
    public List<JobOffer> fetchAll() {
        return cachedFeed();
    }

    private List<JobOffer> cachedFeed() {
        CachedFeed current = cache.get();
        if (current != null && Duration.between(current.fetchedAt(), Instant.now()).compareTo(cacheTtl) < 0) {
            return current.offers();
        }
        List<JobOffer> fresh = fetchFeed();
        // On failure, keep serving the previous snapshot rather than dropping the source entirely.
        if (fresh.isEmpty() && current != null) {
            return current.offers();
        }
        cache.set(new CachedFeed(Instant.now(), fresh));
        return fresh;
    }

    private List<JobOffer> fetchFeed() {
        List<JobOffer> results = new ArrayList<>();
        try {
            String uri = UriComponentsBuilder.fromUriString(API_BASE)
                .queryParam("count", Math.clamp(count, 1, 100))
                .build()
                .encode()
                .toUriString();

            String body = RestClient.create().get().uri(uri).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(body);
            for (JsonNode job : root.path("jobs")) {
                results.add(mapJobOffer(job));
            }
            log.info("Jobicy feed refreshed: {} offers", results.size());
        } catch (Exception e) {
            log.error("Jobicy fetch failed: {}", e.getMessage());
        }
        return results;
    }

    private boolean matchesKeywords(JobOffer offer, String keywords) {
        if (keywords == null || keywords.isBlank()) {
            return true;
        }
        String haystack = (
            (offer.getTitle() == null ? "" : offer.getTitle()) +
            " " +
            (offer.getCompany() == null ? "" : offer.getCompany()) +
            " " +
            (offer.getDescription() == null ? "" : offer.getDescription())
        ).toLowerCase(Locale.ROOT);
        // Every word must appear: an "OR" match would return the whole feed on a two-word query.
        return java.util.Arrays.stream(keywords.toLowerCase(Locale.ROOT).split("\\s+"))
            .filter(word -> !word.isBlank())
            .allMatch(haystack::contains);
    }

    private boolean matchesGeo(JobOffer offer, String geo) {
        if (geo == null) {
            return true;
        }
        String offerGeo = offer.getLocation() == null ? "" : offer.getLocation().toLowerCase(Locale.ROOT);
        // "Anywhere"/"Worldwide" offers stay eligible whatever the searched region.
        return offerGeo.contains(geo) || offerGeo.contains("anywhere") || offerGeo.contains("worldwide") || offerGeo.isBlank();
    }

    private String geoFor(String location) {
        if (location == null || location.isBlank()) {
            return null;
        }
        String lower = location.toLowerCase(Locale.ROOT);
        return GEO_BY_KEYWORD.entrySet()
            .stream()
            .filter(entry -> lower.contains(entry.getKey()))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);
    }

    JobOffer mapJobOffer(JsonNode job) {
        JobOffer offer = new JobOffer();
        offer.setTitle(job.path("jobTitle").asText(""));
        offer.setCompany(blankToNull(job.path("companyName").asText(null)));
        // jobGeo is the eligibility region ("Europe", "Anywhere"), the closest thing to a location.
        offer.setLocation(blankToNull(job.path("jobGeo").asText(null)));
        offer.setDescription(description(job));
        // Canonical Jobicy URL, required by their attribution terms.
        offer.setApplyUrl(blankToNull(job.path("url").asText(null)));
        offer.setSourceId(blankToNull(job.path("id").asText(null)));
        offer.setSource(JobSource.JOBICY);
        offer.setContractType(firstOfArray(job.path("jobType")));
        offer.setExperienceLevel(blankToNull(job.path("jobLevel").asText(null)));
        offer.setRemote(true);
        offer.setPublishedAt(parseDate(job.path("pubDate").asText(null)));
        applySalary(offer, job);
        return offer;
    }

    private String description(JsonNode job) {
        String full = stripHtml(job.path("jobDescription").asText(""));
        return full.isBlank() ? stripHtml(job.path("jobExcerpt").asText("")) : full;
    }

    /**
     * Same rule as the other connectors: only yearly amounts are stored, so the salary component of
     * the match score never compares an hourly rate with an annual package.
     */
    private void applySalary(JobOffer offer, JsonNode job) {
        String period = job.path("salaryPeriod").asText("");
        if (!YEARLY.equalsIgnoreCase(period) && !"annual".equalsIgnoreCase(period)) {
            return;
        }
        Integer min = intOrNull(job.path("salaryMin"));
        Integer max = intOrNull(job.path("salaryMax"));
        if (min == null && max == null) {
            return;
        }
        offer.setSalaryMin(min);
        offer.setSalaryMax(max);
        offer.setSalaryCurrency(blankToNull(job.path("salaryCurrency").asText(null)));
    }

    private static Integer intOrNull(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        double value = node.asDouble(0);
        return value <= 0 ? null : (int) Math.round(value);
    }

    private static String firstOfArray(JsonNode array) {
        return array.isArray() && !array.isEmpty() ? blankToNull(array.get(0).asText(null)) : null;
    }

    private Instant parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            // pubDate carries an offset: 2026-08-12T14:19:54+00:00
            return OffsetDateTime.parse(value).toInstant();
        } catch (Exception e) {
            try {
                return Instant.parse(value);
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String stripHtml(String html) {
        return html == null || html.isBlank() ? "" : Jsoup.parse(html).text();
    }
}
