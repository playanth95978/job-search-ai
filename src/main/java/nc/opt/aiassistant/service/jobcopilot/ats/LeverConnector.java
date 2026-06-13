package nc.opt.aiassistant.service.jobcopilot.ats;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.CandidateProfile;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.domain.enumeration.JobSource;
import org.jsoup.Jsoup;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
@RequiredArgsConstructor
public class LeverConnector implements AtsConnector {

    private static final String API_BASE = "https://api.lever.co/v0/postings";
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(String url) {
        return url != null && url.contains("lever.co");
    }

    @Override
    public JobSource getSource() {
        return JobSource.LEVER;
    }

    @Override
    public List<JobOffer> fetchJobs(String keywords, String location) {
        return List.of();
    }

    /**
     * Lever exposes a public "apply" endpoint on its Postings API
     * (POST /v0/postings/{site}/{postingId}/apply) that companies can enable
     * for their job board. {@code offer.getCompany()} holds the Lever site
     * slug and {@code offer.getSourceId()} the posting id, both captured
     * during {@link #fetchFromCompany(String)}.
     */
    @Override
    public boolean supportsAutoApply() {
        return true;
    }

    @Override
    public ApplyResult applyTo(JobOffer offer, CandidateProfile profile, String coverLetter) {
        if (offer.getCompany() == null || offer.getSourceId() == null) {
            return new ApplyResult(false, "Missing Lever posting reference");
        }
        String url = API_BASE + "/" + offer.getCompany() + "/" + offer.getSourceId() + "/apply";
        try {
            MultipartBodyBuilder form = new MultipartBodyBuilder();
            form.part("name", profile.getFullName() != null ? profile.getFullName() : "");
            form.part("email", profile.getEmail() != null ? profile.getEmail() : "");
            form.part("comments", coverLetter != null ? coverLetter : "");
            if (profile.getRawMarkdown() != null && !profile.getRawMarkdown().isBlank()) {
                ByteArrayResource resume = new ByteArrayResource(profile.getRawMarkdown().getBytes(StandardCharsets.UTF_8)) {
                    @Override
                    public String getFilename() {
                        return "cv.md";
                    }
                };
                form.part("resume", resume);
            }

            RestClient client = RestClient.create();
            client.post().uri(url).contentType(MediaType.MULTIPART_FORM_DATA).body(form.build()).retrieve().toBodilessEntity();

            return new ApplyResult(true, "Application submitted via Lever");
        } catch (RestClientResponseException e) {
            log.warn(
                "Lever auto-apply rejected for {}/{}: {} {}",
                offer.getCompany(),
                offer.getSourceId(),
                e.getStatusCode(),
                e.getMessage()
            );
            return new ApplyResult(false, "Lever rejected the application (" + e.getStatusCode() + "). Apply manually instead.");
        } catch (Exception e) {
            log.error("Lever auto-apply failed for {}/{}: {}", offer.getCompany(), offer.getSourceId(), e.getMessage());
            return new ApplyResult(false, "Lever auto-apply failed: " + e.getMessage());
        }
    }

    /** Fetches jobs from a Lever company page. */
    public List<JobOffer> fetchFromCompany(String company) {
        List<JobOffer> results = new ArrayList<>();
        try {
            RestClient client = RestClient.create();
            String body = client
                .get()
                .uri(API_BASE + "/" + company + "?mode=json")
                .retrieve()
                .body(String.class);

            JsonNode jobs = objectMapper.readTree(body);
            for (JsonNode job : jobs) {
                JobOffer offer = new JobOffer();
                offer.setTitle(job.path("text").asText());
                offer.setCompany(company);
                offer.setDescription(stripHtml(job.path("descriptionPlain").asText("")));
                offer.setApplyUrl(job.path("applyUrl").asText());
                offer.setSourceId(job.path("id").asText());
                offer.setSource(JobSource.LEVER);

                JsonNode cats = job.path("categories");
                offer.setLocation(cats.path("location").asText(null));
                offer.setContractType(cats.path("commitment").asText(null));
                offer.setRemote(isRemote(offer.getLocation(), offer.getTitle()));
                results.add(offer);
            }
        } catch (Exception e) {
            log.error("Lever fetch failed for company={}: {}", company, e.getMessage());
        }
        return results;
    }

    private String stripHtml(String html) {
        return html.isBlank() ? "" : Jsoup.parse(html).text();
    }

    private boolean isRemote(String location, String title) {
        String combined = ((location != null ? location : "") + " " + title).toLowerCase();
        return combined.contains("remote") || combined.contains("télétravail");
    }
}
