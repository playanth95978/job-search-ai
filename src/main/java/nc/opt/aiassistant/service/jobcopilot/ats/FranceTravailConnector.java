package nc.opt.aiassistant.service.jobcopilot.ats;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.domain.enumeration.JobSource;
import nc.opt.aiassistant.service.jobcopilot.ats.support.AtsDates;
import org.apache.commons.lang3.RegExUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;

/**
 * Connector for the France Travail "Offres d'emploi v2" API (OAuth2 client-credentials).
 *
 * <p>Paging goes through the {@code range=start-end} parameter; the API caps a query at 3150 results,
 * hence the ROME grand-domaine partitioning used by the ingestion batch.</p>
 */
@Slf4j
@Component
public final class FranceTravailConnector extends AbstractAtsConnector {

    private static final String API_BASE = "https://api.francetravail.io/partenaire/offresdemploi/v2/offres/search";
    private static final String TOKEN_URL = "https://entreprise.francetravail.fr/connexion/oauth2/access_token?realm=/partenaire";

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int BATCH_PAGE_SIZE = 150;
    /** API hard cap: the range value cannot exceed 3149. */
    private static final int MAX_RESULTS = 3150;
    /** Renew slightly before expiry so a long page never runs with a stale token. */
    private static final int TOKEN_RENEWAL_MARGIN_SECONDS = 60;

    @Value("${application.job-copilot.france-travail.client-id:}")
    private String clientId;

    @Value("${application.job-copilot.france-travail.client-secret:}")
    private String clientSecret;

    private String cachedToken;
    private Instant tokenExpiry;

    public FranceTravailConnector(
        @Qualifier("atsRestClient") RestClient restClient,
        @Qualifier("atsObjectMapper") ObjectMapper objectMapper
    ) {
        super(restClient, objectMapper);
    }

    // --- Payload ------------------------------------------------------------------------------

    record FranceTravailSearch(List<FranceTravailOffer> resultats) {}

    record FranceTravailOffer(
        String id,
        String intitule,
        String description,
        String typeContrat,
        String dateCreation,
        FranceTravailEntreprise entreprise,
        FranceTravailLieu lieuTravail,
        FranceTravailOrigine origineOffre,
        FranceTravailSalaire salaire
    ) {}

    record FranceTravailEntreprise(String nom) {}

    record FranceTravailLieu(String libelle, String codePostal) {}

    record FranceTravailOrigine(String urlOrigine) {}

    record FranceTravailSalaire(String libelle) {}

    record FranceTravailToken(@JsonProperty("access_token") String accessToken, @JsonProperty("expires_in") Integer expiresIn) {}

    // --- Connector ----------------------------------------------------------------------------

    @Override
    public boolean supports(String url) {
        return StringUtils.contains(url, "francetravail");
    }

    @Override
    public JobSource getSource() {
        return JobSource.FRANCE_TRAVAIL;
    }

    private boolean configured() {
        if (StringUtils.isAnyBlank(clientId, clientSecret)) {
            log.warn("France Travail API credentials not configured");
            return false;
        }
        return true;
    }

    @Override
    public List<JobOffer> fetchJobs(String keywords, String location) {
        return configured() ? fetchRange(keywords, location, null, 0, DEFAULT_PAGE_SIZE) : List.of();
    }

    @Override
    public Flux<JobOffer> fetchJobsPaginated(String keywords, String location) {
        return configured() ? paginate(keywords, location, null, DEFAULT_PAGE_SIZE, Integer.MAX_VALUE) : Flux.empty();
    }

    /**
     * Paginates over a single ROME grand domaine (and optionally a département), for mass ingestion.
     *
     * <p>Uses the maximum page size (150) and stops at the API's hard ceiling of 3150 results; beyond
     * that the API rejects the request. A failing page ends the partition gracefully — the other
     * partitions keep running — rather than aborting the whole job.</p>
     */
    public Flux<JobOffer> fetchByGrandDomaine(String grandDomaine, String departement) {
        return configured() ? paginate(null, null, new Partition(grandDomaine, departement), BATCH_PAGE_SIZE, MAX_RESULTS) : Flux.empty();
    }

    /** ROME grand domaine + optional INSEE département, the batch's partitioning key. */
    private record Partition(String grandDomaine, String departement) {}

    private Flux<JobOffer> paginate(String keywords, String location, Partition partition, int pageSize, int maxResults) {
        return Flux.<List<JobOffer>, Integer>generate(
            () -> 0,
            (start, sink) -> {
                if (start >= maxResults) {
                    sink.complete();
                    return start;
                }
                int size = Math.min(start + pageSize, maxResults) - start;
                List<JobOffer> page = fetchRange(keywords, location, partition, start, size);
                if (page.isEmpty()) {
                    sink.complete();
                    return start;
                }
                sink.next(page);
                if (page.size() < pageSize) {
                    sink.complete();
                }
                return start + pageSize;
            }
        ).flatMapIterable(page -> page);
    }

    private List<JobOffer> fetchRange(String keywords, String location, Partition partition, int start, int size) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(API_BASE).queryParam("range", start + "-" + (start + size - 1));
        if (StringUtils.isNotBlank(keywords)) {
            builder.queryParam("motsCles", keywords);
        }
        if (StringUtils.isNotBlank(location)) {
            builder.queryParam("codePostal", location);
        }
        if (partition != null) {
            builder.queryParam("grandDomaine", partition.grandDomaine());
            if (StringUtils.isNotBlank(partition.departement())) {
                builder.queryParam("departement", partition.departement());
            }
        }

        String token = accessToken();
        if (token == null) {
            return List.of();
        }
        // The API answers 204 (empty body) when nothing matches: getJson returns null, i.e. "no page".
        FranceTravailSearch search = getJson(
            builder.build().encode().toUriString(),
            FranceTravailSearch.class,
            header(AUTHORIZATION, "Bearer " + token)
        );
        if (search == null || search.resultats() == null) {
            return List.of();
        }
        return search.resultats().stream().filter(Objects::nonNull).map(this::mapJobOffer).toList();
    }

    /** OAuth2 client-credentials token, cached until shortly before expiry. */
    private synchronized String accessToken() {
        if (cachedToken != null && tokenExpiry != null && tokenExpiry.isAfter(Instant.now().plusSeconds(TOKEN_RENEWAL_MARGIN_SECONDS))) {
            return cachedToken;
        }
        try {
            String form = UriComponentsBuilder.newInstance()
                .queryParam("grant_type", "client_credentials")
                .queryParam("client_id", clientId)
                .queryParam("client_secret", clientSecret)
                .queryParam("scope", "api_offresdemploiv2 o2dsoffre")
                .build()
                .encode()
                .getQuery();

            String body = restClient
                .post()
                .uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class);

            FranceTravailToken token = objectMapper.readValue(body, FranceTravailToken.class);
            cachedToken = token.accessToken();
            tokenExpiry = Instant.now().plusSeconds(token.expiresIn() == null ? 3600 : token.expiresIn());
            return cachedToken;
        } catch (Exception e) {
            log.error("France Travail token request failed: {}", e.getMessage());
            cachedToken = null;
            tokenExpiry = null;
            return null;
        }
    }

    JobOffer mapJobOffer(FranceTravailOffer result) {
        JobOffer offer = new JobOffer();
        offer.setTitle(StringUtils.defaultString(result.intitule()));
        offer.setCompany(result.entreprise() == null ? null : StringUtils.trimToNull(result.entreprise().nom()));
        offer.setLocation(result.lieuTravail() == null ? null : StringUtils.trimToNull(result.lieuTravail().libelle()));
        offer.setDescription(StringUtils.defaultString(result.description()));
        offer.setContractType(StringUtils.trimToNull(result.typeContrat()));
        offer.setApplyUrl(result.origineOffre() == null ? null : StringUtils.trimToNull(result.origineOffre().urlOrigine()));
        offer.setSourceId(StringUtils.trimToNull(result.id()));
        offer.setSource(JobSource.FRANCE_TRAVAIL);
        offer.setRemote(false);
        offer.setPublishedAt(AtsDates.parse(result.dateCreation()));
        if (result.salaire() != null) {
            applySalary(offer, result.salaire().libelle());
        }
        return offer;
    }

    /**
     * {@code salaire.libelle} is free text ("Annuel de 35000,00 Euros à 45000,00 Euros"): the first
     * two integers are taken as the range. Anything else leaves the salary columns empty.
     */
    private void applySalary(JobOffer offer, String libelle) {
        if (StringUtils.isBlank(libelle)) {
            return;
        }
        String[] parts = StringUtils.split(StringUtils.trim(RegExUtils.replaceAll(libelle, "[^0-9\\-]", " ")));
        if (parts == null || parts.length == 0) {
            return;
        }
        Integer min = NumberUtils.toInt(parts[0], -1) > 0 ? NumberUtils.toInt(parts[0]) : null;
        Integer max = parts.length > 1 && NumberUtils.toInt(parts[1], -1) > 0 ? NumberUtils.toInt(parts[1]) : null;
        offer.setSalaryMin(min);
        offer.setSalaryMax(max);
    }
}
