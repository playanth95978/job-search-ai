package nc.opt.aiassistant.service.jobcopilot.ats;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.domain.enumeration.JobSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import reactor.core.publisher.Flux;

@Slf4j
@Component
@RequiredArgsConstructor
public class FranceTravailConnector implements AtsConnector {

    private static final String API_BASE = "https://api.francetravail.io/partenaire/offresdemploi/v2/offres/search";
    private final ObjectMapper objectMapper;

    @Value("${application.job-copilot.france-travail.client-id:}")
    private String clientId;

    @Value("${application.job-copilot.france-travail.client-secret:}")
    private String clientSecret;

    @Override
    public boolean supports(String url) {
        return url != null && url.contains("francetravail");
    }

    @Override
    public JobSource getSource() {
        return JobSource.FRANCE_TRAVAIL;
    }

    @Override
    public List<JobOffer> fetchJobs(String keywords, String location) {
        if (clientId.isBlank() || clientSecret.isBlank()) {
            log.warn("France Travail API credentials not configured");
            return List.of();
        }

        List<JobOffer> results = new ArrayList<>();
        try {
            String token = getAccessToken();
            RestClient client = RestClient.create();
            String body = client
                .get()
                .uri(API_BASE + "?motsCles=" + (keywords != null ? keywords : "") + "&range=0-49")
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(String.class);

            JsonNode root = objectMapper.readTree(body);
            for (JsonNode r : root.path("resultats")) {
                JobOffer jobOffer = mapJobOffer(r);
                results.add(jobOffer);
            }
        } catch (Exception e) {
            log.error("France Travail fetch failed: {}", e.getMessage());
        }
        return results;
    }

    private JobOffer mapJobOffer(JsonNode r) {
        JobOffer offer = new JobOffer();
        offer.setTitle(r.path("intitule").asText());
        offer.setCompany(r.path("entreprise").path("nom").asText(null));
        offer.setLocation(r.path("lieuTravail").path("libelle").asText(null));
        offer.setDescription(r.path("description").asText(""));
        offer.setContractType(r.path("typeContrat").asText(null));
        offer.setApplyUrl(r.path("origineOffre").path("urlOrigine").asText(null));
        offer.setSourceId(r.path("id").asText());
        offer.setSource(JobSource.FRANCE_TRAVAIL);
        offer.setRemote(false);

        JsonNode sal = r.path("salaire");
        if (!sal.isMissingNode() && sal.has("libelle")) {
            parseSalary(sal.path("libelle").asText(""), offer);
        }
        return offer;
    }

    private String getAccessToken() {
        RestClient client = RestClient.create();
        String body = client
            .post()
            .uri("https://entreprise.francetravail.fr/connexion/oauth2/access_token?realm=/partenaire")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body(
                "grant_type=client_credentials&client_id=" +
                    clientId +
                    "&client_secret=" +
                    clientSecret +
                    "&scope=api_offresdemploiv2 o2dsoffre"
            )
            .retrieve()
            .body(String.class);
        try {
            return objectMapper.readTree(body).path("access_token").asText();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to get France Travail token", e);
        }
    }

    private Flux<JobOffer> fetchJobsFt(String keywords, String location) {
        final int pageSize = 50;
        RestClient client = RestClient.create();
        return Flux.generate(
            () -> 0,
            (start, sink) -> {
                try {
                    int end = start + pageSize - 1;
                    String body = client
                        .get()
                        .uri(uriBuilder -> {
                            uriBuilder
                                .path(API_BASE)
                                .queryParam("motsCles", keywords)
                                .queryParam("range", start + "-" + end);
                            if (location != null && !location.isBlank()) {
                                uriBuilder.queryParam("codePostal", location);
                            }
                            return uriBuilder.build();
                        })
                        .header("Authorization", "Bearer " + getAccessToken())
                        .retrieve()
                        .body(String.class);

                    JsonNode root = objectMapper.readTree(body);
                    JsonNode results = root.path("resultats");
                    if (results.isEmpty()) {
                        sink.complete();
                        return start;
                    }
                    for (JsonNode r : results) {
                        JobOffer offer = mapJobOffer(r);
                        sink.next(offer);
                    }
                    // dernière page
                    if (results.size() < pageSize) {
                        sink.complete();
                    }
                    return start + pageSize;
                } catch (Exception e) {
                    sink.error(e);
                    return start;
                }
            }
        ).cast(JobOffer.class);
    }

    private void parseSalary(String libelle, JobOffer offer) {
        try {
            String cleaned = libelle.replaceAll("[^0-9\\-]", " ").trim();
            String[] parts = cleaned.split("\\s+");
            if (parts.length >= 1) offer.setSalaryMin(Integer.parseInt(parts[0]));
            if (parts.length >= 2) offer.setSalaryMax(Integer.parseInt(parts[1]));
        } catch (NumberFormatException ignored) {}
    }
}
