package nc.opt.aiassistant.service.jobcopilot.ats;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
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
 * Connector for the <a href="https://developer.adzuna.com/">Adzuna</a> job aggregation API.
 *
 * <p>Adzuna is keyword + country based (no New Caledonia endpoint — use {@code EmploiNcConnector} for
 * local offers). It is the aggregated FR / international source used to fill the base for the career
 * simulator. A free {@code app_id}/{app_key} pair is required; without it the connector degrades to a
 * no-op so the ingestion batch keeps running.</p>
 */
@Slf4j
@Component
public final class AdzunaConnector extends AbstractAtsConnector {

    private static final String API_BASE = "https://api.adzuna.com/v1/api/jobs";

    @Value("${application.job-copilot.adzuna.app-id:}")
    private String appId;

    @Value("${application.job-copilot.adzuna.app-key:}")
    private String appKey;

    /** Adzuna country code (gb, us, fr, de, au…). Adzuna has no NC endpoint. */
    @Value("${application.job-copilot.adzuna.country:fr}")
    private String country;

    /** Optional location filter ("where"); blank = whole country. */
    @Value("${application.job-copilot.adzuna.where:}")
    private String where;

    @Value("${application.job-copilot.adzuna.results-per-page:50}")
    private int resultsPerPage;

    @Value("${application.job-copilot.adzuna.max-pages:5}")
    private int maxPages;

    /** Seed "what" queries paged through by the scheduled ingestion batch. */
    @Value("${application.job-copilot.adzuna.queries:}")
    private String queriesCsv;

    public AdzunaConnector(@Qualifier("atsRestClient") RestClient restClient, @Qualifier("atsObjectMapper") ObjectMapper objectMapper) {
        super(restClient, objectMapper);
    }

    // --- Payload ------------------------------------------------------------------------------

    record AdzunaSearch(List<AdzunaJob> results) {}

    record AdzunaJob(
        String id,
        String title,
        String description,
        AdzunaCompany company,
        AdzunaLocation location,
        @JsonProperty("redirect_url") String redirectUrl,
        String created,
        @JsonProperty("salary_min") Double salaryMin,
        @JsonProperty("salary_max") Double salaryMax,
        @JsonProperty("contract_type") String contractType,
        @JsonProperty("contract_time") String contractTime
    ) {}

    record AdzunaCompany(@JsonProperty("display_name") String displayName) {}

    record AdzunaLocation(@JsonProperty("display_name") String displayName, List<String> area) {}

    // --- Connector ----------------------------------------------------------------------------

    @Override
    public boolean supports(String url) {
        return StringUtils.contains(url, "adzuna.com");
    }

    @Override
    public JobSource getSource() {
        return JobSource.ADZUNA;
    }

    private boolean configured() {
        if (StringUtils.isAnyBlank(appId, appKey)) {
            log.warn("Adzuna API credentials not configured (application.job-copilot.adzuna.app-id/app-key)");
            return false;
        }
        return true;
    }

    @Override
    public List<JobOffer> fetchJobs(String keywords, String location) {
        return fetchJobs(keywords, location, null);
    }

    /**
     * Adzuna's country is a path segment (…/jobs/{country}/search/…), so the market is chosen per
     * search: {@code country} (fr, gb, us, au, de, ca…) overrides the configured default.
     */
    @Override
    public List<JobOffer> fetchJobs(String keywords, String location, String country) {
        if (!configured()) {
            return List.of();
        }
        String market = StringUtils.isBlank(country) ? this.country : StringUtils.lowerCase(country);
        return fetchPage(keywords, StringUtils.defaultIfBlank(location, where), 1, market);
    }

    @Override
    public Flux<JobOffer> fetchJobsPaginated(String keywords, String location) {
        if (!configured()) {
            return Flux.empty();
        }
        String loc = StringUtils.defaultIfBlank(location, where);
        // Flux.generate emits one page (a List) per call, fanned out with flatMapIterable.
        return Flux.<List<JobOffer>, Integer>generate(
            () -> 1,
            (page, sink) -> {
                if (page > maxPages) {
                    sink.complete();
                    return page;
                }
                List<JobOffer> offers = fetchPage(keywords, loc, page, country);
                if (offers.isEmpty()) {
                    sink.complete();
                    return page;
                }
                sink.next(offers);
                if (offers.size() < resultsPerPage) {
                    sink.complete();
                }
                return page + 1;
            }
        ).flatMapIterable(offers -> offers);
    }

    /**
     * Fetches every configured seed query, paged, for the mass-ingestion batch. Queries run
     * sequentially ({@code concatMap}) to stay within Adzuna's free-tier rate limits.
     */
    public Flux<JobOffer> fetchConfiguredSeeds() {
        if (!configured()) {
            return Flux.empty();
        }
        List<String> queries = csv(queriesCsv);
        if (queries.isEmpty()) {
            log.warn("Adzuna configured but no seed queries (application.job-copilot.adzuna.queries) — nothing to ingest");
            return Flux.empty();
        }
        return Flux.fromIterable(queries).concatMap(query -> fetchJobsPaginated(query, where));
    }

    private List<JobOffer> fetchPage(String what, String location, int page, String market) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(API_BASE + "/" + market + "/search/" + page)
            .queryParam("app_id", appId)
            .queryParam("app_key", appKey)
            .queryParam("results_per_page", resultsPerPage)
            .queryParam("content-type", "application/json");
        if (StringUtils.isNotBlank(what)) {
            builder.queryParam("what", what);
        }
        if (StringUtils.isNotBlank(location)) {
            builder.queryParam("where", location);
        }

        AdzunaSearch search = getJson(builder.build().encode().toUriString(), AdzunaSearch.class);
        if (search == null || search.results() == null) {
            return List.of();
        }
        return search.results().stream().filter(Objects::nonNull).map(this::mapJobOffer).toList();
    }

    JobOffer mapJobOffer(AdzunaJob job) {
        JobOffer offer = new JobOffer();
        offer.setTitle(StringUtils.defaultString(job.title()));
        offer.setCompany(job.company() == null ? null : StringUtils.trimToNull(job.company().displayName()));
        offer.setLocation(job.location() == null ? null : StringUtils.trimToNull(job.location().displayName()));
        offer.setCountry(countryOf(job));
        offer.setDescription(stripHtml(job.description()));
        offer.setApplyUrl(StringUtils.trimToNull(job.redirectUrl()));
        offer.setSourceId(StringUtils.trimToNull(job.id()));
        offer.setSource(JobSource.ADZUNA);
        offer.setSalaryMin(roundedOrNull(job.salaryMin()));
        offer.setSalaryMax(roundedOrNull(job.salaryMax()));
        offer.setContractType(StringUtils.firstNonBlank(job.contractType(), job.contractTime()));
        offer.setPublishedAt(AtsDates.parse(job.created()));
        offer.setRemote(detectRemote(offer.getTitle(), offer.getLocation(), offer.getDescription()));
        return offer;
    }

    /** {@code location.area} is a hierarchy, broadest first: its head is the country. */
    private String countryOf(AdzunaJob job) {
        if (job.location() != null && job.location().area() != null && !job.location().area().isEmpty()) {
            return job.location().area().getFirst();
        }
        return StringUtils.upperCase(country);
    }

    private static Integer roundedOrNull(Double value) {
        return value == null ? null : (int) Math.round(value);
    }
}
