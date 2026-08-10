package nc.opt.aiassistant.service.jobcopilot.ats;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.config.ApplicationProperties;
import nc.opt.aiassistant.domain.CandidateProfile;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.domain.enumeration.JobSource;
import nc.opt.aiassistant.service.pdf.ResumePdfService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * SmartRecruiters connector. Applies via the public Posting API candidate endpoint
 * (POST /v1/companies/{companyId}/postings/{postingId}/candidates), which accepts anonymous
 * applications for postings that enabled it.
 * <p>
 * Auto-apply is gated behind {@code application.job-copilot.smart-recruiters.auto-apply-enabled}
 * (off by default) so the payload can be validated against a controlled posting before going live.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmartRecruitersConnector implements AtsConnector {

    private static final String API_BASE = "https://api.smartrecruiters.com/v1/companies";
    /** SmartRecruiters posting URL: jobs.smartrecruiters.com/{companyId}/{postingId}[-slug]. */
    private static final Pattern URL_REF = Pattern.compile("smartrecruiters\\.com/([^/]+)/(\\d+)");

    private final ObjectMapper objectMapper;
    private final ResumePdfService resumePdfService;
    private final ApplicationProperties applicationProperties;

    @Override
    public boolean supports(String url) {
        return url != null && url.contains("smartrecruiters.com");
    }

    @Override
    public JobSource getSource() {
        return JobSource.SMARTRECRUITERS;
    }

    @Override
    public List<JobOffer> fetchJobs(String keywords, String location) {
        return List.of();
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
    public boolean supportsAutoApply() {
        return applicationProperties.getJobCopilot().getSmartRecruiters().isAutoApplyEnabled();
    }

    @Override
    public ApplyResult applyTo(JobOffer offer, CandidateProfile profile, String coverLetter) {
        PostingRef ref = parseRef(offer.getApplyUrl());
        String companyId = offer.getCompany() != null ? offer.getCompany() : ref != null ? ref.site() : null;
        String postingId = offer.getSourceId() != null ? offer.getSourceId() : ref != null ? ref.postingId() : null;
        if (companyId == null || postingId == null) {
            return new ApplyResult(false, "Missing SmartRecruiters posting reference");
        }
        if (profile == null || profile.getFullName() == null || profile.getEmail() == null) {
            return new ApplyResult(false, "Champs obligatoires manquants (nom, e-mail). Complétez votre profil, puis réessayez.");
        }

        String[] name = splitName(profile.getFullName());
        ObjectNode body = objectMapper.createObjectNode();
        body.put("firstName", name[0]);
        body.put("lastName", name[1]);
        body.put("email", profile.getEmail());
        if (coverLetter != null && !coverLetter.isBlank()) {
            body.put("coverLetter", coverLetter);
        }
        ArrayNode attachments = body.putArray("attachments");
        ObjectNode resume = attachments.addObject();
        resume.put("type", "resume");
        resume.put("fileName", "cv.pdf");
        resume.put("content", Base64.getEncoder().encodeToString(resumePdfService.fromProfile(profile)));

        String url = API_BASE + "/" + companyId + "/postings/" + postingId + "/candidates";
        try {
            RestClient.create().post().uri(url).contentType(MediaType.APPLICATION_JSON).body(body.toString()).retrieve().toBodilessEntity();
            return new ApplyResult(true, "Application submitted via SmartRecruiters");
        } catch (RestClientResponseException e) {
            log.warn("SmartRecruiters auto-apply rejected for {}/{}: {} {}", companyId, postingId, e.getStatusCode(), e.getMessage());
            return new ApplyResult(false, "SmartRecruiters rejected the application (" + e.getStatusCode() + "). Apply manually instead.");
        } catch (Exception e) {
            log.error("SmartRecruiters auto-apply failed for {}/{}: {}", companyId, postingId, e.getMessage());
            return new ApplyResult(false, "SmartRecruiters auto-apply failed: " + e.getMessage());
        }
    }

    /** Fetches postings from a SmartRecruiters company (best-effort; no auth for public postings). */
    @Override
    public List<JobOffer> fetchFromUrl(String url) {
        PostingRef ref = parseRef(url);
        String companyId = ref != null ? ref.site() : AtsConnector.boardSlug(url, "smartrecruiters.com/");
        return fetchFromCompany(companyId);
    }

    public List<JobOffer> fetchFromCompany(String companyId) {
        List<JobOffer> results = new ArrayList<>();
        try {
            String body = RestClient.create()
                .get()
                .uri(API_BASE + "/" + companyId + "/postings")
                .retrieve()
                .body(String.class);
            JsonNode content = objectMapper.readTree(body).path("content");
            for (JsonNode posting : content) {
                JobOffer offer = new JobOffer();
                offer.setTitle(posting.path("name").asText());
                offer.setCompany(companyId);
                offer.setSourceId(posting.path("id").asText());
                offer.setSource(JobSource.SMARTRECRUITERS);
                offer.setApplyUrl("https://jobs.smartrecruiters.com/" + companyId + "/" + posting.path("id").asText());
                JsonNode loc = posting.path("location");
                offer.setLocation(loc.path("city").asText(null));
                results.add(offer);
            }
        } catch (Exception e) {
            log.error("SmartRecruiters fetch failed for company={}: {}", companyId, e.getMessage());
        }
        return results;
    }

    private String[] splitName(String fullName) {
        String trimmed = fullName.trim();
        int sp = trimmed.indexOf(' ');
        if (sp < 0) {
            return new String[] { trimmed, trimmed };
        }
        String first = trimmed.substring(0, sp);
        String last = trimmed.substring(sp + 1).trim();
        return new String[] { first, last.isBlank() ? first : last };
    }
}
