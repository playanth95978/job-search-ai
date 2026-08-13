package nc.opt.aiassistant.service.jobcopilot.ats;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.domain.enumeration.JobSource;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Connector for the <a href="https://docs.recruitee.com/reference/offers">Recruitee careers-site API</a>:
 * {@code GET https://{company}.recruitee.com/api/offers/}, public, no authentication. The company is
 * the careers-site subdomain, so it is read from the host and not from the path.
 *
 * <p>Only {@code status = published} offers are kept, and the description is the concatenation of the
 * {@code description} and {@code requirements} blocks — Recruitee splits them, and the requirements
 * carry most of the skills the matching engine relies on.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecruiteeConnector implements AtsConnector {

    /** Careers-site host: {company}.recruitee.com — the subdomain is the company slug. */
    private static final Pattern SUBDOMAIN = Pattern.compile("https?://([^./]+)\\.recruitee\\.com");
    /** Offer URL: {company}.recruitee.com/o/{slug} (public) or /offers/{id}. */
    private static final Pattern OFFER_SLUG = Pattern.compile("recruitee\\.com/o/([^/?#]+)");

    private final ObjectMapper objectMapper;

    /** Comma-separated Recruitee company subdomains crawled by the ingestion; blank = pasted URLs only. */
    @Value("${application.job-copilot.recruitee.companies:}")
    private String companiesCsv;

    @Override
    public boolean supports(String url) {
        return url != null && url.contains("recruitee.com");
    }

    @Override
    public JobSource getSource() {
        return JobSource.RECRUITEE;
    }

    @Override
    public PostingRef parseRef(String url) {
        if (url == null) {
            return null;
        }
        String company = companyOf(url);
        Matcher slug = OFFER_SLUG.matcher(url);
        return slug.find() ? new PostingRef(company.isBlank() ? null : company, slug.group(1)) : null;
    }

    /** Board-based ATS: no cross-company search endpoint, so keyword search is a no-op. */
    @Override
    public List<JobOffer> fetchJobs(String keywords, String location) {
        return List.of();
    }

    @Override
    public List<JobOffer> fetchFromUrl(String url) {
        String company = companyOf(url);
        return company.isBlank() ? List.of() : fetchFromCompany(company);
    }

    /** Fetches every published offer of the configured companies (ingestion entry point). */
    public List<JobOffer> fetchConfiguredCompanies() {
        List<String> companies = Arrays.stream(companiesCsv.split(","))
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .toList();
        if (companies.isEmpty()) {
            return List.of();
        }
        List<JobOffer> all = new ArrayList<>();
        companies.forEach(company -> all.addAll(fetchFromCompany(company)));
        return all;
    }

    public List<JobOffer> fetchFromCompany(String company) {
        List<JobOffer> results = new ArrayList<>();
        try {
            String body = RestClient.create()
                .get()
                .uri("https://" + company + ".recruitee.com/api/offers/")
                .retrieve()
                .body(String.class);

            JsonNode root = objectMapper.readTree(body);
            for (JsonNode offer : root.path("offers")) {
                if (!"published".equalsIgnoreCase(offer.path("status").asText(""))) {
                    continue;
                }
                results.add(mapJobOffer(offer, company));
            }
        } catch (Exception e) {
            log.error("Recruitee fetch failed for company={}: {}", company, e.getMessage());
        }
        return results;
    }

    private String companyOf(String url) {
        Matcher m = SUBDOMAIN.matcher(url);
        return m.find() && !"www".equals(m.group(1)) ? m.group(1) : "";
    }

    JobOffer mapJobOffer(JsonNode offer, String company) {
        JobOffer job = new JobOffer();
        job.setTitle(offer.path("title").asText(""));
        job.setCompany(company);
        job.setLocation(buildLocation(offer));
        job.setCountry(blankToNull(offer.path("locations").path(0).path("country_code").asText(null)));
        job.setDescription(buildDescription(offer));
        job.setApplyUrl(firstNonBlank(offer.path("careers_apply_url").asText(null), offer.path("careers_url").asText(null)));
        job.setSourceId(blankToNull(offer.path("id").asText(null)));
        job.setSource(JobSource.RECRUITEE);
        job.setContractType(blankToNull(offer.path("employment_type_code").asText(null)));
        job.setExperienceLevel(blankToNull(offer.path("experience_code").asText(null)));
        job.setRemote(offer.path("remote").asBoolean(false));
        job.setPublishedAt(parseTimestamp(offer.path("published_at").asText(null)));
        applySalary(job, offer.path("salary"));
        return job;
    }

    private String buildLocation(JsonNode offer) {
        JsonNode first = offer.path("locations").path(0);
        List<String> parts = new ArrayList<>();
        addIfPresent(parts, first.path("city").asText(null));
        addIfPresent(parts, first.path("country").asText(null));
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private String buildDescription(JsonNode offer) {
        String description = stripHtml(offer.path("description").asText(""));
        String requirements = stripHtml(offer.path("requirements").asText(""));
        if (requirements.isBlank()) {
            return description;
        }
        return description.isBlank() ? requirements : description + "\n\n" + requirements;
    }

    private void applySalary(JobOffer job, JsonNode salary) {
        if (salary.isMissingNode() || salary.isNull()) {
            return;
        }
        Integer min = intOrNull(salary.path("min"));
        Integer max = intOrNull(salary.path("max"));
        if (min == null && max == null) {
            return;
        }
        job.setSalaryMin(min);
        job.setSalaryMax(max);
        job.setSalaryCurrency(blankToNull(salary.path("currency").asText(null)));
    }

    private static Integer intOrNull(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        double value = node.asDouble(0);
        return value <= 0 ? null : (int) Math.round(value);
    }

    private Instant parseTimestamp(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            try {
                // Recruitee also returns zone-less timestamps (2026-07-30T09:12:00).
                return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC);
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private static void addIfPresent(List<String> parts, String value) {
        if (value != null && !value.isBlank()) {
            parts.add(value);
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String stripHtml(String html) {
        return html == null || html.isBlank() ? "" : Jsoup.parse(html).text();
    }
}
