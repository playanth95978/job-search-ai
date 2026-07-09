package nc.opt.aiassistant.service.jobcopilot.ats;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.domain.enumeration.JobSource;
import org.jsoup.Jsoup;
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
@RequiredArgsConstructor
public class AdzunaConnector implements AtsConnector {

    private static final String API_BASE = "https://api.adzuna.com/v1/api/jobs";

    private final ObjectMapper objectMapper;

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

    @Override
    public boolean supports(String url) {
        return url != null && url.contains("adzuna.com");
    }

    @Override
    public JobSource getSource() {
        return JobSource.ADZUNA;
    }

    private boolean configured() {
        if (appId.isBlank() || appKey.isBlank()) {
            log.warn("Adzuna API credentials not configured (application.job-copilot.adzuna.app-id/app-key)");
            return false;
        }
        return true;
    }

    @Override
    public List<JobOffer> fetchJobs(String keywords, String location) {
        if (!configured()) {
            return List.of();
        }
        return fetchPage(keywords, location != null && !location.isBlank() ? location : where, 1);
    }

    @Override
    public Flux<JobOffer> fetchJobsPaginated(String keywords, String location) {
        if (!configured()) {
            return Flux.empty();
        }
        String loc = location != null && !location.isBlank() ? location : where;
        // Flux.generate emits one page (a List) per call, fanned out with flatMapIterable.
        return Flux.<List<JobOffer>, Integer>generate(
            () -> 1,
            (page, sink) -> {
                if (page > maxPages) {
                    sink.complete();
                    return page;
                }
                List<JobOffer> offers = fetchPage(keywords, loc, page);
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
        ).flatMapIterable(p -> p);
    }

    /**
     * Fetches every configured seed query, paged, for the mass-ingestion batch. Queries run
     * sequentially ({@code concatMap}) to stay within Adzuna's free-tier rate limits.
     */
    public Flux<JobOffer> fetchConfiguredSeeds() {
        if (!configured()) {
            return Flux.empty();
        }
        List<String> queries = Arrays.stream(queriesCsv.split(","))
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .toList();
        if (queries.isEmpty()) {
            log.warn("Adzuna configured but no seed queries (application.job-copilot.adzuna.queries) — nothing to ingest");
            return Flux.empty();
        }
        return Flux.fromIterable(queries).concatMap(q -> fetchJobsPaginated(q, where));
    }

    private List<JobOffer> fetchPage(String what, String location, int page) {
        List<JobOffer> results = new ArrayList<>();
        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(API_BASE + "/" + country + "/search/" + page)
                .queryParam("app_id", appId)
                .queryParam("app_key", appKey)
                .queryParam("results_per_page", resultsPerPage)
                .queryParam("content-type", "application/json");
            if (what != null && !what.isBlank()) {
                builder.queryParam("what", what);
            }
            if (location != null && !location.isBlank()) {
                builder.queryParam("where", location);
            }
            URI uri = builder.build().encode().toUri();

            String body = RestClient.create().get().uri(uri).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(body);
            for (JsonNode r : root.path("results")) {
                results.add(mapJobOffer(r));
            }
        } catch (Exception e) {
            log.error("Adzuna fetch failed (what='{}', page={}): {}", what, page, e.getMessage());
        }
        return results;
    }

    private JobOffer mapJobOffer(JsonNode r) {
        JobOffer offer = new JobOffer();
        offer.setTitle(r.path("title").asText(""));
        offer.setCompany(r.path("company").path("display_name").asText(null));

        JsonNode location = r.path("location");
        offer.setLocation(location.path("display_name").asText(null));
        JsonNode area = location.path("area");
        offer.setCountry(area.isArray() && !area.isEmpty() ? area.get(0).asText(null) : country.toUpperCase());

        String description = Jsoup.parse(r.path("description").asText("")).text();
        offer.setDescription(description);
        offer.setApplyUrl(r.path("redirect_url").asText(null));
        offer.setSourceId(r.path("id").asText());
        offer.setSource(JobSource.ADZUNA);
        offer.setRemote(detectRemote(offer.getTitle(), description, offer.getLocation()));

        if (r.has("salary_min")) {
            offer.setSalaryMin((int) Math.round(r.path("salary_min").asDouble()));
        }
        if (r.has("salary_max")) {
            offer.setSalaryMax((int) Math.round(r.path("salary_max").asDouble()));
        }
        String contract = r.path("contract_type").asText(r.path("contract_time").asText(null));
        offer.setContractType(contract);

        String created = r.path("created").asText(null);
        if (created != null && !created.isBlank()) {
            try {
                offer.setPublishedAt(Instant.parse(created));
            } catch (Exception ignored) {
                // Adzuna occasionally returns non-ISO timestamps — leave publishedAt null.
            }
        }
        return offer;
    }

    private boolean detectRemote(String title, String description, String location) {
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
