package nc.opt.aiassistant.service.jobcopilot.ats;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.domain.enumeration.JobSource;
import nc.opt.aiassistant.service.jobcopilot.ats.support.AtsDates;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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
 * but enforced: without it the API answers 403 "Undeclared referrer"). The account also whitelists
 * the calling server's IP. The market is chosen with {@code locale_code} (fr_FR, en_AU, en_GB…),
 * which is how Careerjet partitions its index. Missing credentials degrade the connector to a no-op.</p>
 */
@Slf4j
@Component
public final class CareerjetConnector extends AbstractAtsConnector {

    private static final String API_BASE = "https://search.api.careerjet.net/v4/query";

    /** Salary period codes: Y(early), M(onthly), W(eekly), D(aily), H(ourly). */
    private static final String YEARLY = "Y";

    /** The API caps the page parameter at 10. */
    private static final int MAX_PAGE = 10;

    /** Market → locale_code mapping for the country-aware search used by the job-search service. */
    private static final Map<String, String> LOCALE_BY_COUNTRY = Map.of(
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

    /** Sent as documented, but observed to be ignored: the API always returns 20 results per page. */
    @Value("${application.job-copilot.careerjet.page-size:50}")
    private int pageSize;

    @Value("${application.job-copilot.careerjet.max-pages:5}")
    private int maxPages;

    public CareerjetConnector(@Qualifier("atsRestClient") RestClient restClient, @Qualifier("atsObjectMapper") ObjectMapper objectMapper) {
        super(restClient, objectMapper);
    }

    // --- Payload ------------------------------------------------------------------------------

    record CareerjetResponse(String type, String message, Integer hits, Integer pages, List<CareerjetJob> jobs) {}

    record CareerjetJob(
        String title,
        String company,
        String date,
        String description,
        String locations,
        String salary,
        String site,
        String url,
        @JsonProperty("salary_min") Double salaryMin,
        @JsonProperty("salary_max") Double salaryMax,
        @JsonProperty("salary_type") String salaryType,
        @JsonProperty("salary_currency_code") String salaryCurrencyCode
    ) {}

    /** One page of results plus the total page count the API reports for the query. */
    private record PageResult(List<JobOffer> offers, int pages) {}

    // --- Connector ----------------------------------------------------------------------------

    @Override
    public boolean supports(String url) {
        return StringUtils.contains(url, "careerjet.");
    }

    @Override
    public JobSource getSource() {
        return JobSource.CAREERJET;
    }

    private boolean configured() {
        if (StringUtils.isBlank(apiKey)) {
            log.warn("Careerjet API key not configured (application.job-copilot.careerjet.api-key)");
            return false;
        }
        if (StringUtils.isBlank(userIp)) {
            log.warn("Careerjet requires user_ip (application.job-copilot.careerjet.user-ip) — connector disabled");
            return false;
        }
        if (StringUtils.isBlank(referer)) {
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
                if (page > Math.min(maxPages, MAX_PAGE)) {
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
        ).flatMapIterable(offers -> offers);
    }

    /** Careerjet partitions its index by locale, so a market code selects the right one. */
    private String localeFor(String country) {
        return StringUtils.isBlank(country) ? localeCode : LOCALE_BY_COUNTRY.getOrDefault(StringUtils.lowerCase(country), localeCode);
    }

    private PageResult fetchPage(String keywords, String location, int page, String locale) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(API_BASE)
            .queryParam("locale_code", locale)
            .queryParam("user_ip", userIp)
            .queryParam("user_agent", userAgent)
            .queryParam("page", page)
            .queryParam("page_size", Math.clamp(pageSize, 1, 100));
        if (StringUtils.isNotBlank(keywords)) {
            builder.queryParam("keywords", keywords);
        }
        if (StringUtils.isNotBlank(location)) {
            builder.queryParam("location", location);
        }

        CareerjetResponse response = getJson(
            builder.build().encode().toUriString(),
            CareerjetResponse.class,
            headers(AUTHORIZATION, basicAuth(apiKey, ""), REFERER, referer)
        );
        if (response == null) {
            return new PageResult(List.of(), 0);
        }
        if (!"JOBS".equals(response.type())) {
            log.warn("Careerjet returned type='{}' ({})", response.type(), StringUtils.defaultString(response.message()));
            return new PageResult(List.of(), 0);
        }
        List<JobOffer> offers =
            response.jobs() == null
                ? List.of()
                : response
                      .jobs()
                      .stream()
                      .filter(Objects::nonNull)
                      .map(job -> mapJobOffer(job, locale))
                      .toList();
        return new PageResult(offers, response.pages() == null ? 0 : response.pages());
    }

    JobOffer mapJobOffer(CareerjetJob job, String locale) {
        JobOffer offer = new JobOffer();
        offer.setTitle(StringUtils.defaultString(job.title()));
        offer.setCompany(StringUtils.trimToNull(job.company()));
        offer.setLocation(StringUtils.trimToNull(job.locations()));
        offer.setDescription(stripHtml(job.description()));
        offer.setApplyUrl(StringUtils.trimToNull(job.url()));
        offer.setSource(JobSource.CAREERJET);
        // Careerjet exposes no id, and `url` is a signed jobviewtrack redirect that changes on every
        // call — using it as the identity would re-create every offer at each ingestion, since
        // deduplication keys on (source, sourceId). The posting's own invariants are hashed instead.
        offer.setSourceId(stableId(job.title(), job.company(), job.locations(), job.date()));
        offer.setCountry(countryOf(locale));
        offer.setPublishedAt(AtsDates.parse(job.date()));
        applySalary(offer, job);
        offer.setRemote(detectRemote(offer.getTitle(), offer.getLocation(), offer.getDescription()));
        return offer;
    }

    /**
     * Salary is only kept when it is expressed per year: {@code salary_min}/{@code salary_max} are
     * amounts *for the given period*, and storing an hourly rate in the same column as an annual one
     * would corrupt the salary-based part of the matching score.
     */
    private void applySalary(JobOffer offer, CareerjetJob job) {
        if (!YEARLY.equals(job.salaryType())) {
            return;
        }
        Integer min = positiveOrNull(job.salaryMin());
        Integer max = positiveOrNull(job.salaryMax());
        if (min == null && max == null) {
            return;
        }
        offer.setSalaryMin(min);
        offer.setSalaryMax(max);
        offer.setSalaryCurrency(StringUtils.trimToNull(job.salaryCurrencyCode()));
    }

    private static Integer positiveOrNull(Double value) {
        return value == null || value <= 0 ? null : (int) Math.round(value);
    }

    /** locale_code is language_COUNTRY (fr_FR) — the ISO country is what the offer carries. */
    private static String countryOf(String locale) {
        String country = StringUtils.substringAfter(StringUtils.defaultString(locale), "_");
        return StringUtils.isBlank(country) ? null : StringUtils.upperCase(country);
    }
}
