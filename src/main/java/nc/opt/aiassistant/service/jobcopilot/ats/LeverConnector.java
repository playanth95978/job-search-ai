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
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class LeverConnector implements AtsConnector {

    private static final String API_BASE = "https://api.lever.co/v0/postings";
    /** Lever posting URL: jobs.lever.co/{site}/{postingId} (postingId is a UUID), optionally /apply. */
    private static final Pattern URL_REF = Pattern.compile("lever\\.co/([^/]+)/([0-9a-fA-F-]{36})");

    private final ObjectMapper objectMapper;

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

    /** Fetches jobs from a Lever company page. */
    @Override
    public List<JobOffer> fetchFromUrl(String url) {
        return fetchFromCompany(AtsConnector.boardSlug(url, "lever.co/"));
    }

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
