package nc.opt.aiassistant.service.jobcopilot.ats;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.domain.enumeration.JobSource;
import nc.opt.aiassistant.service.jobcopilot.ats.support.AtsDates;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
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
public final class EmploiNcConnector extends AbstractAtsConnector {

    private static final String RECORDS_API =
        "https://data.gouv.nc/api/explore/v2.1/catalog/datasets/offres-d-emploi-deposees-sur-le-site-emploi-nc/records";

    /** Public per-offer page pattern on emploi.nc; the {@code uuid} field is appended. */
    private static final String OFFER_URL_PREFIX = "https://emploi.nc/offers/";

    private static final int PAGE_SIZE = 50;

    public EmploiNcConnector(@Qualifier("atsRestClient") RestClient restClient, @Qualifier("atsObjectMapper") ObjectMapper objectMapper) {
        super(restClient, objectMapper);
    }

    // --- Payload ------------------------------------------------------------------------------

    record EmploiNcRecords(List<EmploiNcOffer> results) {}

    /** Opendatasoft flattens the dataset's fields at the record root. */
    record EmploiNcOffer(
        String uuid,
        String titre,
        String enseigne,
        String designation,
        String ville,
        String ville_physique,
        String type_contrat,
        String description,
        String email,
        String telephone,
        String date_debut,
        String statut
    ) {}

    // --- Connector ----------------------------------------------------------------------------

    @Override
    public boolean supports(String url) {
        return StringUtils.containsAny(url, "emploi.nc", "emploi.gouv.nc");
    }

    @Override
    public JobSource getSource() {
        return JobSource.EMPLOI_NC;
    }

    @Override
    public List<JobOffer> fetchFromUrl(String url) {
        // A bare emploi.nc URL maps to the active-offers feed.
        return fetchJobs(null, null);
    }

    @Override
    public List<JobOffer> fetchJobs(String keywords, String location) {
        return fetchPage(keywords, location, 0);
    }

    @Override
    public Flux<JobOffer> fetchJobsPaginated(String keywords, String location) {
        // Flux.generate allows at most one sink.next() per call, so each call emits a whole page
        // (a List) and flatMapIterable fans it out into individual offers.
        return Flux.<List<JobOffer>, Integer>generate(
            () -> 0,
            (offset, sink) -> {
                List<JobOffer> page = fetchPage(keywords, location, offset);
                if (page.isEmpty()) {
                    sink.complete();
                    return offset;
                }
                sink.next(page);
                if (page.size() < PAGE_SIZE) {
                    sink.complete();
                }
                return offset + PAGE_SIZE;
            }
        ).flatMapIterable(page -> page);
    }

    private List<JobOffer> fetchPage(String keywords, String location, int offset) {
        String uri = UriComponentsBuilder.fromUriString(RECORDS_API)
            .queryParam("where", buildWhere(keywords, location))
            .queryParam("limit", PAGE_SIZE)
            .queryParam("offset", offset)
            .queryParam("order_by", "date_debut desc")
            .build()
            .encode()
            .toUriString();

        EmploiNcRecords records = getJson(uri, EmploiNcRecords.class);
        if (records == null || records.results() == null) {
            return List.of();
        }
        return records.results().stream().filter(Objects::nonNull).map(this::mapJobOffer).toList();
    }

    /** Builds an ODSQL {@code where} clause filtering on active offers, keywords and city. */
    private String buildWhere(String keywords, String location) {
        StringBuilder where = new StringBuilder("statut=\"ACTIVE\"");
        if (StringUtils.isNotBlank(keywords)) {
            where.append(" and search(\"").append(sanitize(keywords)).append("\")");
        }
        if (StringUtils.isNotBlank(location)) {
            where.append(" and search(ville, \"").append(sanitize(location)).append("\")");
        }
        return where.toString();
    }

    /** Removes characters that would break the ODSQL string literal. */
    private String sanitize(String value) {
        return StringUtils.trim(StringUtils.replace(value, "\"", " "));
    }

    JobOffer mapJobOffer(EmploiNcOffer record) {
        JobOffer offer = new JobOffer();
        offer.setTitle(StringUtils.trimToNull(record.titre()));
        offer.setCompany(StringUtils.firstNonBlank(record.enseigne(), record.designation()));
        offer.setLocation(StringUtils.firstNonBlank(record.ville(), record.ville_physique()));
        offer.setContractType(StringUtils.trimToNull(record.type_contrat()));
        offer.setApplyUrl(record.uuid() == null ? OFFER_URL_PREFIX : OFFER_URL_PREFIX + record.uuid() + "#top");
        offer.setSourceId(StringUtils.trimToNull(record.uuid()));
        offer.setSource(JobSource.EMPLOI_NC);
        offer.setRemote(false);
        offer.setPublishedAt(AtsDates.parse(record.date_debut()));
        offer.setDescription(buildDescription(record));
        return offer;
    }

    /**
     * Builds a non-empty description. The dataset's {@code description} field is frequently null,
     * which would make the offer fail ingestion enrichment, so we always synthesize a fallback.
     */
    private String buildDescription(EmploiNcOffer record) {
        if (StringUtils.isNotBlank(record.description())) {
            return record.description();
        }
        StringBuilder sb = new StringBuilder("Offre ").append(StringUtils.defaultIfBlank(record.titre(), "emploi"));
        if (StringUtils.isNotBlank(record.type_contrat())) {
            sb.append(" — ").append(record.type_contrat());
        }
        String city = StringUtils.firstNonBlank(record.ville(), record.ville_physique());
        if (StringUtils.isNotBlank(city)) {
            sb.append(" à ").append(city);
        }
        sb.append('.');
        if (StringUtils.isNotBlank(record.email())) {
            sb.append(" Contact : ").append(record.email()).append('.');
        }
        if (StringUtils.isNotBlank(record.telephone())) {
            sb.append(" Tél : ").append(record.telephone()).append('.');
        }
        return sb.append(" Source : emploi.nc.").toString();
    }
}
