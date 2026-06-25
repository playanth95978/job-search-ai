package nc.opt.aiassistant.service.jobcopilot.ats;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.domain.enumeration.JobSource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * Connector for New Caledonia job offers published on emploi.nc / emploi.gouv.nc,
 * exposed through the Open Data NC (Opendatasoft Explore v2.1) public API.
 *
 * <p>No authentication is required. France Travail does not cover New Caledonia, so this
 * is the local source for Caledonian offers.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmploiNcConnector implements AtsConnector {

    private static final String RECORDS_API =
        "https://data.gouv.nc/api/explore/v2.1/catalog/datasets/offres-d-emploi-deposees-sur-le-site-emploi-nc/records";

    /** Public per-offer page pattern on emploi.nc; the {@code uuid} field is appended. */
    private static final String OFFER_URL_PREFIX = "https://emploi.nc/offers/";

    private static final int PAGE_SIZE = 50;

    private final ObjectMapper objectMapper;
    private final org.springframework.web.reactive.function.client.WebClient.Builder webClientBuilder;

    @Override
    public boolean supports(String url) {
        return url != null && (url.contains("emploi.nc") || url.contains("emploi.gouv.nc"));
    }

    @Override
    public JobSource getSource() {
        return JobSource.EMPLOI_NC;
    }

    @Override
    public List<JobOffer> fetchJobs(String keywords, String location) {
        return fetchJobsPaginated(keywords, location).collectList().block();
    }

    @Override
    public Flux<JobOffer> fetchJobsPaginated(String keywords, String location) {
        // Flux.generate allows at most one sink.next() per call, so each call emits a whole page
        // (a List) and flatMapIterable fans it out into individual offers.
        return Flux.<List<JobOffer>, Integer>generate(
            () -> 0,
            (offset, sink) -> {
                try {
                    String where = buildWhere(keywords, location);
                    String body = webClientBuilder
                        .build()
                        .get()
                        .uri(RECORDS_API, uriBuilder ->
                            uriBuilder
                                .queryParam("where", where)
                                .queryParam("limit", PAGE_SIZE)
                                .queryParam("offset", offset)
                                .queryParam("order_by", "date_debut desc")
                                .build()
                        )
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                    JsonNode root = objectMapper.readTree(body);
                    JsonNode results = root.path("results");
                    if (results.isEmpty()) {
                        sink.complete();
                        return offset;
                    }
                    java.util.List<JobOffer> page = new java.util.ArrayList<>();
                    for (JsonNode r : results) {
                        page.add(mapJobOffer(r));
                    }
                    sink.next(page);
                    if (results.size() < PAGE_SIZE) {
                        sink.complete();
                    }
                    return offset + PAGE_SIZE;
                } catch (Exception e) {
                    sink.error(e);
                    return offset;
                }
            }
        ).flatMapIterable(page -> page);
    }

    /** Builds an ODSQL {@code where} clause filtering on active offers, keywords and city. */
    private String buildWhere(String keywords, String location) {
        StringBuilder where = new StringBuilder("statut=\"ACTIVE\"");
        if (keywords != null && !keywords.isBlank()) {
            where.append(" and search(\"").append(sanitize(keywords)).append("\")");
        }
        if (location != null && !location.isBlank()) {
            where.append(" and search(ville, \"").append(sanitize(location)).append("\")");
        }
        return where.toString();
    }

    /** Removes characters that would break the ODSQL string literal. */
    private String sanitize(String value) {
        return value.replace("\"", " ").trim();
    }

    private JobOffer mapJobOffer(JsonNode r) {
        String uuid = textOrNull(r, "uuid");
        JobOffer offer = new JobOffer();
        offer.setTitle(textOrNull(r, "titre"));
        offer.setCompany(firstNonBlank(textOrNull(r, "enseigne"), textOrNull(r, "designation")));
        offer.setLocation(firstNonBlank(textOrNull(r, "ville"), textOrNull(r, "ville_physique")));
        offer.setContractType(textOrNull(r, "type_contrat"));
        offer.setApplyUrl(uuid != null ? OFFER_URL_PREFIX + uuid + "#top" : OFFER_URL_PREFIX);
        offer.setSourceId(uuid);
        offer.setSource(JobSource.EMPLOI_NC);
        offer.setRemote(false);
        offer.setDescription(buildDescription(r));
        return offer;
    }

    /**
     * Builds a non-empty description. The dataset's {@code description} field is frequently null,
     * which would make the offer fail ingestion enrichment, so we always synthesize a fallback.
     */
    private String buildDescription(JsonNode r) {
        String description = textOrNull(r, "description");
        if (description != null && !description.isBlank()) {
            return description;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Offre ").append(firstNonBlank(textOrNull(r, "titre"), "emploi"));
        String contract = textOrNull(r, "type_contrat");
        if (contract != null) sb.append(" — ").append(contract);
        String city = firstNonBlank(textOrNull(r, "ville"), textOrNull(r, "ville_physique"));
        if (city != null) sb.append(" à ").append(city);
        sb.append('.');
        String email = textOrNull(r, "email");
        String phone = textOrNull(r, "telephone");
        if (email != null) sb.append(" Contact : ").append(email).append('.');
        if (phone != null) sb.append(" Tél : ").append(phone).append('.');
        sb.append(" Source : emploi.nc.");
        return sb.toString();
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) return null;
        String text = value.asText();
        return text.isBlank() ? null : text;
    }

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }
}
