package nc.opt.aiassistant.service.jobcopilot.ats;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
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
public class GreenhouseConnector implements AtsConnector {

    private static final String API_BASE = "https://boards-api.greenhouse.io/v1/boards";
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(String url) {
        return url != null && url.contains("greenhouse.io");
    }

    @Override
    public JobSource getSource() {
        return JobSource.GREENHOUSE;
    }

    @Override
    public List<JobOffer> fetchJobs(String keywords, String location) {
        return List.of();
    }

    /** Fetches jobs from a specific Greenhouse board (company slug). */
    public List<JobOffer> fetchFromBoard(String boardToken) {
        List<JobOffer> results = new ArrayList<>();
        try {
            RestClient client = RestClient.create();
            String body = client
                .get()
                .uri(API_BASE + "/" + boardToken + "/jobs?content=true")
                .retrieve()
                .body(String.class);

            JsonNode root = objectMapper.readTree(body);
            JsonNode jobs = root.path("jobs");
            for (JsonNode job : jobs) {
                JobOffer offer = new JobOffer();
                offer.setTitle(job.path("title").asText());
                offer.setCompany(boardToken);
                offer.setLocation(extractLocation(job));
                offer.setDescription(stripHtml(job.path("content").asText("")));
                offer.setApplyUrl(job.path("absolute_url").asText());
                offer.setSourceId(job.path("id").asText());
                offer.setSource(JobSource.GREENHOUSE);
                offer.setRemote(isRemote(offer.getLocation(), offer.getTitle()));
                results.add(offer);
            }
        } catch (Exception e) {
            log.error("Greenhouse fetch failed for board={}: {}", boardToken, e.getMessage());
        }
        return results;
    }

    private String extractLocation(JsonNode job) {
        JsonNode loc = job.path("location").path("name");
        return loc.isMissingNode() ? null : loc.asText();
    }

    private String stripHtml(String html) {
        return html.isBlank() ? "" : Jsoup.parse(html).text();
    }

    private boolean isRemote(String location, String title) {
        String combined = ((location != null ? location : "") + " " + title).toLowerCase();
        return combined.contains("remote") || combined.contains("télétravail");
    }
}
