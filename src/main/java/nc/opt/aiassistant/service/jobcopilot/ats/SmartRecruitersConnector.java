package nc.opt.aiassistant.service.jobcopilot.ats;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.domain.enumeration.JobSource;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Connecteur SmartRecruiters, en <b>lecture seule</b> : il lit les offres publiées d'un board
 * public. La soumission de candidature via l'API Posting a été retirée — postuler au nom du
 * candidat sans son geste explicite sort du cadre des CGU de l'ATS.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmartRecruitersConnector implements AtsConnector {

    private static final String API_BASE = "https://api.smartrecruiters.com/v1/companies";
    /** SmartRecruiters posting URL: jobs.smartrecruiters.com/{companyId}/{postingId}[-slug]. */
    private static final Pattern URL_REF = Pattern.compile("smartrecruiters\\.com/([^/]+)/(\\d+)");

    private final ObjectMapper objectMapper;

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
}
