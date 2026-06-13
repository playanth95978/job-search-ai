package nc.opt.aiassistant.service.jobcopilot.ats;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
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
public class RemoteOkConnector implements AtsConnector {

    private static final String API_URL = "https://remoteok.com/api";
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(String url) {
        return url != null && url.contains("remoteok.com");
    }

    @Override
    public JobSource getSource() {
        return JobSource.REMOTEOK;
    }

    @Override
    public List<JobOffer> fetchJobs(String keywords, String location) {
        List<JobOffer> results = new ArrayList<>();
        try {
            RestClient client = RestClient.builder().defaultHeader("User-Agent", "JobCopilot/1.0").build();
            String body = client.get().uri(API_URL).retrieve().body(String.class);

            JsonNode nodes = objectMapper.readTree(body);
            for (JsonNode node : nodes) {
                if (!node.has("position")) continue;

                String title = node.path("position").asText("");
                if (keywords != null && !title.toLowerCase().contains(keywords.toLowerCase())) continue;

                JobOffer offer = new JobOffer();
                offer.setTitle(title);
                offer.setCompany(node.path("company").asText());
                offer.setLocation(node.path("location").asText("Remote"));
                offer.setRemote(true);
                offer.setDescription(stripHtml(node.path("description").asText("")));
                offer.setApplyUrl(node.path("url").asText());
                offer.setSourceId(node.path("id").asText());
                offer.setSource(JobSource.REMOTEOK);

                if (node.has("tags")) {
                    String skills = StreamSupport.stream(node.path("tags").spliterator(), false)
                        .map(JsonNode::asText)
                        .collect(Collectors.joining(","));
                    offer.setSkills("[\"" + skills.replace(",", "\",\"") + "\"]");
                }

                results.add(offer);
            }
        } catch (Exception e) {
            log.error("RemoteOK fetch failed: {}", e.getMessage());
        }
        return results;
    }

    private String stripHtml(String html) {
        return html.isBlank() ? "" : Jsoup.parse(html).text();
    }
}
