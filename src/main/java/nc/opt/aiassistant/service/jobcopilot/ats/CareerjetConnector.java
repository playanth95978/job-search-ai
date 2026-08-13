package nc.opt.aiassistant.service.jobcopilot.ats;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.domain.enumeration.JobSource;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;

/**
 * Connector for the <a href="https://www.careerjet.com/partners/api/">Careerjet Search API v4</a>.
 *
 * <p>{@code GET https://search.api.careerjet.net/v4/query} with HTTP Basic auth built from the API key
 * ({@code Base64(apiKey + ":")}). The API <strong>requires</strong> {@code user_ip} and
 * {@code user_agent} to be forwarded — they identify the end user whose action triggered the call —
 * plus a {@code Referer} header matching the site declared on the Careerjet account (undocumented,
 * but enforced: without it the API answers 403 "Undeclared referrer").
 * The market is chosen with {@code locale_code} (fr_FR, en_AU, en_GB…), which is how Careerjet
 * partitions its index. Without a key the connector degrades to a no-op.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CareerjetConnector implements AtsConnector {

    private static final String API_BASE = "https://search.api.careerjet.net/v4/query";

    /** Salary period codes returned by the API: Y(early), M(onthly), W(eekly), D(aily), H(ourly). */
    private static final String YEARLY = "Y";

    /** Dates come back in full RFC 1123 form: "Tue, 11 Aug 2026 05:04:02 GMT". */
    private static final DateTimeFormatter RFC_DATE = DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.ENGLISH);

    /** Market → locale_code mapping for the country-aware search used by the job-search service. */
    private static final java.util.Map<String, String> LOCALE_BY_COUNTRY = java.util.Map.of(
        "fr",
        "fr_FR",
        "au",
        "en_AU",
        "gb",
        "en_GB",
        "us",
        "en_US",
        "ca",
        "en_CA",
        "de",
        "de_DE",
        "nz",
        "en_NZ"
    );

    /** MessageDigest is not thread-safe and connectors are shared singletons. */
    private static final ThreadLocal<MessageDigest> DIGEST = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    });

    private final ObjectMapper objectMapper;

    @Value("${application.job-copilot.careerjet.api-key:}")
    private String apiKey;

    /** Default market, e.g. fr_FR for France, en_AU for Australia. */
    @Value("${application.job-copilot.careerjet.locale-code:fr_FR}")
    private String localeCode;

    /**
     * IP forwarded as {@code user_ip}. Careerjet requires the *end user's* IP; the caller's address is
     * not available in the batch, so a configured server IP is used for scheduled ingestion.
     */
    @Value("${application.job-copilot.careerjet.user-ip:}")
    private String userIp;

    @Value("${application.job-copilot.careerjet.user-agent:Mozilla/5.0 (compatible; JobSearchAI/1.0)}")
    private String userAgent;

    /** Must match the site declared on the Careerjet partner account, or every call is rejected. */
    @Value("${application.job-copilot.careerjet.referer:}")
    private String referer;

    /** 1..100, per the API. */
    @Value("${application.job-copilot.careerjet.page-size:50}")
    private int pageSize;

    /** 1..10, per the API. */
    @Value("${application.job-copilot.careerjet.max-pages:5}")
    private int maxPages;

    @Override
    public boolean supports(String url) {
        return url != null && url.contains("careerjet.");
    }

    @Override
    public JobSource getSource() {
        return JobSource.CAREERJET;
    }

    private boolean configured() {
        if (apiKey.isBlank()) {
            log.warn("Careerjet API key not configured (application.job-copilot.careerjet.api-key)");
            return false;
        }
        if (userIp.isBlank()) {
            log.warn("Careerjet requires user_ip (application.job-copilot.careerjet.user-ip) — connector disabled");
            return false;
        }
        if (referer.isBlank()) {
            log.warn("Careerjet requires a declared Referer (application.job-copilot.careerjet.referer) — connector disabled");
            return false;
        }
        return true;
    }

    @Override
    public List<JobOffer> fetchJobs(String keywords, String location) {
        return fetchJobs(keywords, location, null);
    }

    @Override
    public List<JobOffer> fetchJobs(String keywords, String location, String country) {
        if (!configured()) {
            return List.of();
        }
        return fetchPage(keywords, location, 1, localeFor(country)).offers();
    }

    @Override
    public Flux<JobOffer> fetchJobsPaginated(String keywords, String location) {
        if (!configured()) {
            return Flux.empty();
        }
        return Flux.<List<JobOffer>, Integer>generate(
            () -> 1,
            (page, sink) -> {
                // The API caps `page` at 10.
                if (page > Math.min(maxPages, 10)) {
                    sink.complete();
                    return page;
                }
                PageResult result = fetchPage(keywords, location, page, localeCode);
                if (result.offers().isEmpty()) {
                    sink.complete();
                    return page;
                }
                sink.next(result.offers());
                // Stop on the response's own page count: `page_size` is documented but ignored by the
                // API (always 20 per page), so a "short page" tells us nothing about the end.
                if (page >= result.pages()) {
                    sink.complete();
                }
                return page + 1;
            }
        ).flatMapIterable(p -> p);
    }

    /** Careerjet partitions its index by locale, so a market code selects the right one. */
    private String localeFor(String country) {
        if (country == null || country.isBlank()) {
            return localeCode;
        }
        return LOCALE_BY_COUNTRY.getOrDefault(country.toLowerCase(), localeCode);
    }

    /** One page of results plus the total page count the API reports for the query. */
    private record PageResult(List<JobOffer> offers, int pages) {}

    private PageResult fetchPage(String keywords, String location, int page, String locale) {
        List<JobOffer> results = new ArrayList<>();
        int pages = 0;
        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(API_BASE)
                .queryParam("locale_code", locale)
                .queryParam("user_ip", userIp)
                .queryParam("user_agent", userAgent)
                .queryParam("page", page)
                .queryParam("page_size", Math.clamp(pageSize, 1, 100));
            if (keywords != null && !keywords.isBlank()) {
                builder.queryParam("keywords", keywords);
            }
            if (location != null && !location.isBlank()) {
                builder.queryParam("location", location);
            }
            URI uri = builder.build().encode().toUri();

            String body = RestClient.create()
                .get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, basicAuth())
                .header(HttpHeaders.REFERER, referer)
                .retrieve()
                .body(String.class);

            JsonNode root = objectMapper.readTree(body);
            String type = root.path("type").asText("");
            if (!"JOBS".equals(type)) {
                log.warn("Careerjet returned type='{}' ({})", type, root.path("message").asText(""));
                return new PageResult(results, 0);
            }
            pages = root.path("pages").asInt(0);
            for (JsonNode job : root.path("jobs")) {
                results.add(mapJobOffer(job, locale));
            }
        } catch (Exception e) {
            log.error("Careerjet fetch failed (keywords='{}', page={}): {}", keywords, page, e.getMessage());
        }
        return new PageResult(results, pages);
    }

    /** Basic auth with an empty password, as documented: {@code Base64(api_key:)}. */
    private String basicAuth() {
        String token = Base64.getEncoder().encodeToString((apiKey + ":").getBytes(StandardCharsets.UTF_8));
        return "Basic " + token;
    }

    JobOffer mapJobOffer(JsonNode job, String locale) {
        JobOffer offer = new JobOffer();
        offer.setTitle(job.path("title").asText(""));
        offer.setCompany(blankToNull(job.path("company").asText(null)));
        offer.setLocation(blankToNull(job.path("locations").asText(null)));
        offer.setDescription(stripHtml(job.path("description").asText("")));
        offer.setApplyUrl(blankToNull(job.path("url").asText(null)));
        offer.setSource(JobSource.CAREERJET);
        offer.setSourceId(stableId(job));
        offer.setCountry(countryOf(locale));
        offer.setPublishedAt(parseDate(job.path("date").asText(null)));
        applySalary(offer, job);
        offer.setRemote(detectRemote(offer.getTitle(), offer.getLocation(), offer.getDescription()));
        return offer;
    }

    /**
     * Salary is only kept when it is expressed per year: {@code salary_min}/{@code salary_max} are
     * amounts *for the given period*, and storing an hourly rate in the same column as an annual one
     * would corrupt the salary-based part of the matching score.
     */
    private void applySalary(JobOffer offer, JsonNode job) {
        if (!YEARLY.equals(job.path("salary_type").asText(null))) {
            return;
        }
        Integer min = intOrNull(job.path("salary_min"));
        Integer max = intOrNull(job.path("salary_max"));
        if (min == null && max == null) {
            return;
        }
        offer.setSalaryMin(min);
        offer.setSalaryMax(max);
        offer.setSalaryCurrency(blankToNull(job.path("salary_currency_code").asText(null)));
    }

    private static Integer intOrNull(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        double value = node.asDouble(0);
        return value <= 0 ? null : (int) Math.round(value);
    }

    /**
     * Careerjet exposes no id, and {@code url} is a signed jobviewtrack redirect that <b>changes on
     * every call</b> — using it as the identity would re-create every offer at each ingestion, since
     * deduplication keys on (source, sourceId). The posting's own invariants are hashed instead.
     */
    private String stableId(JsonNode job) {
        String seed = String.join(
            "|",
            job.path("title").asText(""),
            job.path("company").asText(""),
            job.path("locations").asText(""),
            job.path("date").asText("")
        );
        return HexFormat.of()
            .formatHex(DIGEST.get().digest(seed.getBytes(StandardCharsets.UTF_8)))
            .substring(0, 32);
    }

    /** locale_code is language_COUNTRY (fr_FR) — the ISO country is what the offer carries. */
    private static String countryOf(String locale) {
        int underscore = locale == null ? -1 : locale.indexOf('_');
        return underscore > 0 ? locale.substring(underscore + 1).toUpperCase() : null;
    }

    private Instant parseDate(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        try {
            return ZonedDateTime.parse(date, RFC_DATE).toInstant();
        } catch (Exception e) {
            try {
                // Some locales return a plain ISO date instead.
                return LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant();
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

    private boolean detectRemote(String title, String location, String description) {
        String haystack = (
            (title == null ? "" : title) +
            " " +
            (location == null ? "" : location) +
            " " +
            (description == null ? "" : description)
        ).toLowerCase();
        return haystack.contains("remote") || haystack.contains("télétravail") || haystack.contains("teletravail");
    }
}
