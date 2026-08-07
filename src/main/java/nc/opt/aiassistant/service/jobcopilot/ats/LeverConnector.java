package nc.opt.aiassistant.service.jobcopilot.ats;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.CandidateProfile;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.domain.enumeration.JobSource;
import nc.opt.aiassistant.service.pdf.ResumePdfService;
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
    /** Lever posting URL: jobs.lever.co/{site}/{postingId} (postingId is a UUID), optionally /apply. */
    private static final Pattern URL_REF = Pattern.compile("lever\\.co/([^/]+)/([0-9a-fA-F-]{36})");

    private final ObjectMapper objectMapper;
    private final ResumePdfService resumePdfService;

    @Override
    public boolean supports(String url) {
        return url != null && url.contains("lever.co");
    }

    @Override
    public PostingRef parseRef(String url) {
        if (url == null) {
            return null;
        }
        Matcher m = URL_REF.matcher(url);
        return m.find() ? new PostingRef(m.group(1), m.group(2)) : null;
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
        // Prefer the ids captured at fetch time; fall back to parsing the offer's apply URL
        // (aggregator offers that link to a Lever board).
        PostingRef ref = parseRef(offer.getApplyUrl());
        String site = offer.getCompany() != null ? offer.getCompany() : ref != null ? ref.site() : null;
        String postingId = offer.getSourceId() != null ? offer.getSourceId() : ref != null ? ref.postingId() : null;
        if (site == null || postingId == null) {
            return new ApplyResult(false, "Missing Lever posting reference");
        }
        if (profile == null || profile.getFullName() == null || profile.getEmail() == null) {
            return new ApplyResult(false, "Champs obligatoires manquants (nom, e-mail). Complétez votre profil, puis réessayez.");
        }
        String url = API_BASE + "/" + site + "/" + postingId + "/apply";
        try {
            MultipartBodyBuilder form = new MultipartBodyBuilder();
            form.part("name", profile.getFullName());
            form.part("email", profile.getEmail());
            if (coverLetter != null && !coverLetter.isBlank()) {
                form.part("comments", coverLetter);
            }
            byte[] pdf = resumePdfService.fromProfile(profile);
            ByteArrayResource resume = new ByteArrayResource(pdf) {
                @Override
                public String getFilename() {
                    return "cv.pdf";
                }
            };
            form.part("resume", resume).contentType(MediaType.APPLICATION_PDF);

            RestClient client = RestClient.create();
            client.post().uri(url).contentType(MediaType.MULTIPART_FORM_DATA).body(form.build()).retrieve().toBodilessEntity();

            return new ApplyResult(true, "Application submitted via Lever");
        } catch (RestClientResponseException e) {
            log.warn("Lever auto-apply rejected for {}/{}: {} {}", site, postingId, e.getStatusCode(), e.getMessage());
            return new ApplyResult(false, "Lever rejected the application (" + e.getStatusCode() + "). Apply manually instead.");
        } catch (Exception e) {
            log.error("Lever auto-apply failed for {}/{}: {}", site, postingId, e.getMessage());
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
