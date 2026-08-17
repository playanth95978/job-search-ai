package nc.opt.aiassistant.service.jobcopilot.ats;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.domain.enumeration.JobSource;
import nc.opt.aiassistant.service.jobcopilot.ats.support.AtsDates;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
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
public final class RecruiteeConnector extends AbstractAtsConnector {

    /** Careers-site host: {company}.recruitee.com — the subdomain is the company slug. */
    private static final Pattern SUBDOMAIN = Pattern.compile("https?://([^./]+)\\.recruitee\\.com");
    /** Offer URL: {company}.recruitee.com/o/{slug} (public) or /offers/{id}. */
    private static final Pattern OFFER_SLUG = Pattern.compile("recruitee\\.com/o/([^/?#]+)");

    private static final String PUBLISHED = "published";

    /** Comma-separated Recruitee company subdomains crawled by the ingestion; blank = pasted URLs only. */
    @Value("${application.job-copilot.recruitee.companies:}")
    private String companiesCsv;

    public RecruiteeConnector(@Qualifier("atsRestClient") RestClient restClient, @Qualifier("atsObjectMapper") ObjectMapper objectMapper) {
        super(restClient, objectMapper);
    }

    // --- Payload ------------------------------------------------------------------------------

    record RecruiteeOffers(List<RecruiteeOffer> offers) {}

    record RecruiteeOffer(
        Long id,
        String slug,
        String title,
        String status,
        String description,
        String requirements,
        Boolean remote,
        @JsonProperty("employment_type_code") String employmentTypeCode,
        @JsonProperty("experience_code") String experienceCode,
        List<RecruiteeLocation> locations,
        RecruiteeSalary salary,
        @JsonProperty("careers_url") String careersUrl,
        @JsonProperty("careers_apply_url") String careersApplyUrl,
        @JsonProperty("published_at") String publishedAt
    ) {}

    record RecruiteeLocation(String city, String country, @JsonProperty("country_code") String countryCode, String state) {}

    record RecruiteeSalary(Integer min, Integer max, String currency, String period) {}

    // --- Connector ----------------------------------------------------------------------------

    @Override
    public boolean supports(String url) {
        return StringUtils.contains(url, "recruitee.com");
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
        return slug.find() ? new PostingRef(StringUtils.trimToNull(company), slug.group(1)) : null;
    }

    /** Board-based ATS: no cross-company search endpoint, so keyword search is a no-op. */
    @Override
    public List<JobOffer> fetchJobs(String keywords, String location) {
        return List.of();
    }

    @Override
    public List<JobOffer> fetchBySlug(String slug) {
        return StringUtils.isBlank(slug) ? List.of() : fetchFromCompany(slug);
    }

    @Override
    public List<JobOffer> fetchFromUrl(String url) {
        String company = companyOf(url);
        return StringUtils.isBlank(company) ? List.of() : fetchFromCompany(company);
    }

    /** Fetches every published offer of the configured companies (ingestion entry point). */
    public List<JobOffer> fetchConfiguredCompanies() {
        List<JobOffer> all = new ArrayList<>();
        csv(companiesCsv).forEach(company -> all.addAll(fetchFromCompany(company)));
        return all;
    }

    public List<JobOffer> fetchFromCompany(String company) {
        RecruiteeOffers payload = getJson("https://" + company + ".recruitee.com/api/offers/", RecruiteeOffers.class);
        if (payload == null || payload.offers() == null) {
            return List.of();
        }
        return payload
            .offers()
            .stream()
            .filter(Objects::nonNull)
            .filter(offer -> StringUtils.equalsIgnoreCase(offer.status(), PUBLISHED))
            .map(offer -> mapJobOffer(offer, company))
            .toList();
    }

    private String companyOf(String url) {
        Matcher m = SUBDOMAIN.matcher(url);
        return m.find() && !"www".equals(m.group(1)) ? m.group(1) : "";
    }

    JobOffer mapJobOffer(RecruiteeOffer offer, String company) {
        JobOffer job = new JobOffer();
        job.setTitle(StringUtils.defaultString(offer.title()));
        job.setCompany(company);
        job.setLocation(buildLocation(offer));
        job.setCountry(firstLocation(offer) == null ? null : StringUtils.trimToNull(firstLocation(offer).countryCode()));
        job.setDescription(buildDescription(offer));
        job.setApplyUrl(StringUtils.firstNonBlank(offer.careersApplyUrl(), offer.careersUrl()));
        job.setSourceId(offer.id() == null ? null : String.valueOf(offer.id()));
        job.setSource(JobSource.RECRUITEE);
        job.setContractType(StringUtils.trimToNull(offer.employmentTypeCode()));
        job.setExperienceLevel(StringUtils.trimToNull(offer.experienceCode()));
        job.setRemote(BooleanUtils.isTrue(offer.remote()));
        job.setPublishedAt(AtsDates.parse(offer.publishedAt()));
        applySalary(job, offer.salary());
        return job;
    }

    private RecruiteeLocation firstLocation(RecruiteeOffer offer) {
        return offer.locations() == null || offer.locations().isEmpty() ? null : offer.locations().getFirst();
    }

    private String buildLocation(RecruiteeOffer offer) {
        RecruiteeLocation first = firstLocation(offer);
        if (first == null) {
            return null;
        }
        Object[] parts = java.util.stream.Stream.of(first.city(), first.country()).filter(StringUtils::isNotBlank).toArray();
        return parts.length == 0 ? null : StringUtils.join(parts, ", ");
    }

    private String buildDescription(RecruiteeOffer offer) {
        String description = stripHtml(offer.description());
        String requirements = stripHtml(offer.requirements());
        if (StringUtils.isBlank(requirements)) {
            return description;
        }
        return StringUtils.isBlank(description) ? requirements : description + "\n\n" + requirements;
    }

    private void applySalary(JobOffer job, RecruiteeSalary salary) {
        if (salary == null) {
            return;
        }
        Integer min = positiveOrNull(salary.min());
        Integer max = positiveOrNull(salary.max());
        if (min == null && max == null) {
            return;
        }
        job.setSalaryMin(min);
        job.setSalaryMax(max);
        job.setSalaryCurrency(StringUtils.trimToNull(salary.currency()));
    }

    private static Integer positiveOrNull(Integer value) {
        return value == null || value <= 0 ? null : value;
    }
}
