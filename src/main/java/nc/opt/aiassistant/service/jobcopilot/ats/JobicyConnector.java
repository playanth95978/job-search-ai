package nc.opt.aiassistant.service.jobcopilot.ats;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.domain.enumeration.JobSource;
import nc.opt.aiassistant.service.jobcopilot.ats.support.AtsDates;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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
public final class JobicyConnector extends AbstractAtsConnector {

    private static final String API_BASE = "https://jobicy.com/api/v2/remote-jobs";

    /** Job URL: jobicy.com/jobs/{id}-{slug}. */
    private static final Pattern URL_REF = Pattern.compile("jobicy\\.com/jobs/(\\d+)");

    /** Only yearly amounts land in the salary columns, so they stay comparable across sources. */
    private static final String[] YEARLY_PERIODS = { "yearly", "annual" };

    /** Offers open to any region stay eligible whatever the searched location. */
    private static final List<String> WORLDWIDE_MARKERS = List.of("anywhere", "worldwide");

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

    /** 1..100 per the API; 100 is the documented maximum and the default. */
    @Value("${application.job-copilot.jobicy.count:100}")
    private int count;

    /** Minimum interval between two calls to Jobicy — their terms say at most one per hour. */
    @Value("${application.job-copilot.jobicy.cache-ttl:1h}")
    private Duration cacheTtl;

    private final AtomicReference<CachedFeed> cache = new AtomicReference<>();

    public JobicyConnector(@Qualifier("atsRestClient") RestClient restClient, @Qualifier("atsObjectMapper") ObjectMapper objectMapper) {
        super(restClient, objectMapper);
    }

    private record CachedFeed(Instant fetchedAt, List<JobOffer> offers) {}

    // --- Payload ------------------------------------------------------------------------------

    record JobicyFeed(List<JobicyJob> jobs) {}

    record JobicyJob(
        Long id,
        String url,
        String jobSlug,
        String jobTitle,
        String companyName,
        List<String> jobIndustry,
        List<String> jobType,
        String jobGeo,
        String jobLevel,
        String jobExcerpt,
        String jobDescription,
        String pubDate,
        Integer salaryMin,
        Integer salaryMax,
        String salaryCurrency,
        String salaryPeriod
    ) {}

    // --- Connector ----------------------------------------------------------------------------

    @Override
    public boolean supports(String url) {
        return StringUtils.contains(url, "jobicy.com");
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
        String geo = geoFor(location);
        return cachedFeed()
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
        JobicyFeed feed = getJson(API_BASE + "?count=" + Math.clamp(count, 1, 100), JobicyFeed.class);
        if (feed == null || feed.jobs() == null) {
            return List.of();
        }
        List<JobOffer> offers = feed.jobs().stream().filter(Objects::nonNull).map(this::mapJobOffer).toList();
        log.info("Jobicy feed refreshed: {} offers", offers.size());
        return offers;
    }

    private boolean matchesKeywords(JobOffer offer, String keywords) {
        if (StringUtils.isBlank(keywords)) {
            return true;
        }
        String haystack = StringUtils.lowerCase(
            StringUtils.join(new String[] { offer.getTitle(), offer.getCompany(), offer.getDescription() }, ' ')
        );
        // Every word must appear: an "OR" match would return the whole feed on a two-word query.
        return Arrays.stream(StringUtils.split(StringUtils.lowerCase(keywords))).allMatch(haystack::contains);
    }

    private boolean matchesGeo(JobOffer offer, String geo) {
        if (geo == null) {
            return true;
        }
        String offerGeo = StringUtils.lowerCase(StringUtils.trimToEmpty(offer.getLocation()));
        return offerGeo.isEmpty() || offerGeo.contains(geo) || WORLDWIDE_MARKERS.stream().anyMatch(offerGeo::contains);
    }

    private String geoFor(String location) {
        if (StringUtils.isBlank(location)) {
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

    JobOffer mapJobOffer(JobicyJob job) {
        JobOffer offer = new JobOffer();
        offer.setTitle(StringUtils.defaultString(job.jobTitle()));
        offer.setCompany(StringUtils.trimToNull(job.companyName()));
        // jobGeo is the eligibility region ("Europe", "Anywhere"), the closest thing to a location.
        offer.setLocation(StringUtils.trimToNull(job.jobGeo()));
        offer.setDescription(StringUtils.firstNonBlank(stripHtml(job.jobDescription()), stripHtml(job.jobExcerpt())));
        // Canonical Jobicy URL, required by their attribution terms.
        offer.setApplyUrl(StringUtils.trimToNull(job.url()));
        offer.setSourceId(job.id() == null ? null : String.valueOf(job.id()));
        offer.setSource(JobSource.JOBICY);
        offer.setContractType(first(job.jobType()));
        offer.setExperienceLevel(StringUtils.trimToNull(job.jobLevel()));
        offer.setRemote(true);
        offer.setPublishedAt(AtsDates.parse(job.pubDate()));
        applySalary(offer, job);
        return offer;
    }

    /**
     * Same rule as the other connectors: only yearly amounts are stored, so the salary component of
     * the match score never compares an hourly rate with an annual package.
     */
    private void applySalary(JobOffer offer, JobicyJob job) {
        if (!StringUtils.equalsAnyIgnoreCase(job.salaryPeriod(), YEARLY_PERIODS)) {
            return;
        }
        Integer min = positiveOrNull(job.salaryMin());
        Integer max = positiveOrNull(job.salaryMax());
        if (min == null && max == null) {
            return;
        }
        offer.setSalaryMin(min);
        offer.setSalaryMax(max);
        offer.setSalaryCurrency(StringUtils.trimToNull(job.salaryCurrency()));
    }

    private static Integer positiveOrNull(Integer value) {
        return value == null || value <= 0 ? null : value;
    }

    private static String first(List<String> values) {
        return values == null || values.isEmpty() ? null : StringUtils.trimToNull(values.getFirst());
    }
}
