package nc.opt.aiassistant.service.jobcopilot.ats;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.domain.enumeration.EmbeddingStatus;
import nc.opt.aiassistant.domain.enumeration.JobSource;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Disabled by default: Seek fronts its site with bot mitigation (Kasada) that a server-side HTML
 * fetch cannot pass, so the connector is only registered when {@code application.job-copilot.seek.enabled=true}.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "application.job-copilot.seek", name = "enabled", havingValue = "true")
public final class SeekConnector extends AbstractAtsConnector {

    private static final String SEEK_BASE = "https://www.seek.com.au";

    public SeekConnector(
        @org.springframework.beans.factory.annotation.Qualifier("atsRestClient") org.springframework.web.client.RestClient restClient,
        @org.springframework.beans.factory.annotation.Qualifier("atsObjectMapper") ObjectMapper objectMapper
    ) {
        // Seek exposes no API: the connector reads the schema.org JSON-LD embedded in the HTML, whose
        // shape varies per page — JsonNode stays the right tool there, records would not fit.
        super(restClient, objectMapper);
    }

    @Override
    public boolean supports(String url) {
        return url != null && url.contains("seek.com.au");
    }

    @Override
    public JobSource getSource() {
        return JobSource.SEEK;
    }

    @Override
    public List<JobOffer> fetchJobs(String keywords, String location) {
        List<JobOffer> results = new ArrayList<>();
        try {
            results = fetchViaHtmlScraping(keywords, location);
        } catch (org.jsoup.HttpStatusException e) {
            // Seek fronts its site with bot mitigation (Kasada) that requires JS execution, so a
            // server-side HTML fetch is routinely answered with 403/429. This is expected, not an
            // outage: log it quietly and return no results rather than surfacing it as an error.
            if (e.getStatusCode() == 403 || e.getStatusCode() == 429) {
                log.info("Seek blocked automated access (HTTP {}) for '{}' — skipping this source", e.getStatusCode(), keywords);
            } else {
                log.warn("Seek fetch failed (HTTP {}): {}", e.getStatusCode(), e.getMessage());
            }
        } catch (Exception e) {
            log.warn("Seek fetch failed: {}", e.getMessage());
        }
        return results;
    }

    private List<JobOffer> fetchViaHtmlScraping(String keywords, String location) throws Exception {
        List<JobOffer> results = new ArrayList<>();
        String searchSlug = buildSearchSlug(keywords);
        String url = SEEK_BASE + "/" + searchSlug;
        if (location != null && !location.isBlank()) {
            url += "-in-" + encode(location).toLowerCase().replace("+", "-");
        }

        Document doc = Jsoup.connect(url)
            .userAgent("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/125.0 Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "en-AU,en;q=0.9")
            .referrer(SEEK_BASE)
            .timeout(15_000)
            .get();

        Elements scripts = doc.select("script[type='application/ld+json']");
        for (Element script : scripts) {
            try {
                JsonNode json = objectMapper.readTree(script.html());
                if (json.has("@type") && "JobPosting".equals(json.path("@type").asText())) {
                    results.add(parseJobPosting(json));
                } else if (json.isArray()) {
                    for (JsonNode item : json) {
                        if ("JobPosting".equals(item.path("@type").asText())) {
                            results.add(parseJobPosting(item));
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        if (results.isEmpty()) {
            results = parseFromHtml(doc);
        }

        return results;
    }

    private List<JobOffer> parseFromHtml(Document doc) {
        List<JobOffer> results = new ArrayList<>();
        // Seek tags each result card with stable data-automation attributes.
        Elements articles = doc.select("article[data-automation='normalJob'], article[data-automation='premiumJob']");
        if (articles.isEmpty()) {
            articles = doc.select("article[data-testid='job-card'], article");
        }

        for (Element article : articles) {
            try {
                JobOffer offer = new JobOffer();
                String title = extractText(article, "[data-automation='jobTitle']");
                if (title.isBlank()) continue;
                offer.setTitle(title);

                offer.setCompany(extractText(article, "[data-automation='jobCompany']"));
                String location = extractText(article, "[data-automation='jobLocation'], [data-automation='jobCardLocation']");
                offer.setLocation(location);
                offer.setSource(JobSource.SEEK);

                // Short teaser shown on the card — usable description until the offer is enriched on click.
                String teaser = extractText(article, "[data-automation='jobShortDescription']");
                offer.setDescription(teaser);

                offer.setRemote(isRemote(location, title));

                Element link = article.selectFirst("a[data-automation='jobTitle'], a[href*='/job/']");
                if (link != null) {
                    String href = link.absUrl("href");
                    offer.setApplyUrl(href);
                    offer.setSourceId(extractJobId(href));
                }

                String salary = extractText(article, "[data-automation='jobSalary']");
                if (!salary.isBlank()) parseSalary(salary, offer);

                // Scraped from the list: only a teaser so far — enrich lazily, don't embed yet.
                offer.setEmbeddingStatus(EmbeddingStatus.STUB);

                results.add(offer);
                if (results.size() >= 50) break;
            } catch (Exception e) {
                log.debug("Failed to parse Seek card: {}", e.getMessage());
            }
        }
        return results;
    }

    /**
     * Fetches the full description (and structured data) of a single offer from its detail page.
     * One HTTP request per call — meant to be triggered lazily when a user opens the offer,
     * which keeps the request rate well below Seek's anti-bot thresholds.
     *
     * @return the parsed offer, or {@code null} if the page could not be fetched/parsed.
     */
    @Override
    public JobOffer fetchDetail(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/125.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "en-AU,en;q=0.9")
                .referrer(SEEK_BASE)
                .timeout(15_000)
                .get();

            for (Element script : doc.select("script[type='application/ld+json']")) {
                try {
                    JsonNode json = objectMapper.readTree(script.html());
                    if ("JobPosting".equals(json.path("@type").asText())) {
                        return parseJobPosting(json);
                    }
                    if (json.isArray()) {
                        for (JsonNode item : json) {
                            if ("JobPosting".equals(item.path("@type").asText())) {
                                return parseJobPosting(item);
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            // Fallback: full description block rendered in the page.
            String details = extractText(doc, "[data-automation='jobAdDetails']");
            if (!details.isBlank()) {
                JobOffer offer = new JobOffer();
                offer.setDescription(details);
                offer.setSource(JobSource.SEEK);
                return offer;
            }
        } catch (Exception e) {
            log.warn("Seek detail fetch failed for {}: {}", url, e.getMessage());
        }
        return null;
    }

    private JobOffer parseJobPosting(JsonNode json) {
        JobOffer offer = new JobOffer();
        offer.setTitle(json.path("title").asText(""));
        offer.setDescription(json.path("description").asText(""));
        offer.setSource(JobSource.SEEK);
        offer.setApplyUrl(json.path("url").asText(null));

        JsonNode org = json.path("hiringOrganization");
        if (!org.isMissingNode()) {
            offer.setCompany(org.path("name").asText(null));
        }

        JsonNode loc = json.path("jobLocation");
        if (loc.isArray() && !loc.isEmpty()) {
            offer.setLocation(loc.get(0).path("address").path("addressLocality").asText(null));
        } else if (!loc.isMissingNode()) {
            offer.setLocation(loc.path("address").path("addressLocality").asText(null));
        }

        offer.setRemote(isRemote(offer.getLocation(), offer.getTitle()));

        JsonNode salary = json.path("baseSalary");
        if (!salary.isMissingNode()) {
            JsonNode value = salary.path("value");
            if (value.has("minValue")) offer.setSalaryMin((int) value.path("minValue").asDouble());
            if (value.has("maxValue")) offer.setSalaryMax((int) value.path("maxValue").asDouble());
        }

        if (offer.getApplyUrl() != null) {
            offer.setSourceId(extractJobId(offer.getApplyUrl()));
        }

        return offer;
    }

    private String extractText(Element parent, String cssSelector) {
        Element el = parent.selectFirst(cssSelector);
        return el != null ? el.text().trim() : "";
    }

    private String extractJobId(String url) {
        if (url == null) return null;
        int idx = url.lastIndexOf('/');
        if (idx >= 0 && idx < url.length() - 1) {
            String id = url.substring(idx + 1).replaceAll("[^0-9]", "");
            return id.isEmpty() ? String.valueOf(url.hashCode()) : id;
        }
        return String.valueOf(url.hashCode());
    }

    private String buildSearchSlug(String keywords) {
        if (keywords == null || keywords.isBlank()) return "jobs";
        return keywords.toLowerCase().trim().replaceAll("\\s+", "-") + "-jobs";
    }

    private boolean isRemote(String location, String title) {
        String combined = ((location != null ? location : "") + " " + title).toLowerCase();
        return combined.contains("remote") || combined.contains("work from home");
    }

    private void parseSalary(String text, JobOffer offer) {
        try {
            String cleaned = text.replaceAll("[^0-9\\-]", " ").trim();
            String[] parts = cleaned.split("\\s+");
            if (parts.length >= 1 && !parts[0].isEmpty()) offer.setSalaryMin(Integer.parseInt(parts[0]));
            if (parts.length >= 2 && !parts[1].isEmpty()) offer.setSalaryMax(Integer.parseInt(parts[1]));
        } catch (NumberFormatException ignored) {}
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
