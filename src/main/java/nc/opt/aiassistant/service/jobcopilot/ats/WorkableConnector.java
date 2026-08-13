package nc.opt.aiassistant.service.jobcopilot.ats;

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
 * Connector for <a href="https://help.workable.com/hc/en-us/articles/115012771647">Workable's public
 * careers-page API</a>: {@code GET https://www.workable.com/api/accounts/{account}?details=true},
 * no API key, published jobs only.
 *
 * <p>That documented URL 302-redirects to the widget host, which is what we call directly here — the
 * shared {@code RestClient} does not follow cross-host redirects, and both serve the same payload.
 * The authenticated {@code /spi/v3/jobs} endpoint is deliberately not used: it needs the employer's
 * own token and returns drafts/closed postings.</p>
 */
@Slf4j
@Component
public final class WorkableConnector extends AbstractAtsConnector {

    private static final String API_BASE = "https://apply.workable.com/api/v1/widget/accounts/";

    /** Posting URLs: apply.workable.com/j/{SHORTCODE} or apply.workable.com/{account}/j/{SHORTCODE}. */
    private static final Pattern ACCOUNT_POSTING = Pattern.compile("apply\\.workable\\.com/([^/]+)/j/([A-Za-z0-9]+)");
    private static final Pattern DIRECT_POSTING = Pattern.compile("apply\\.workable\\.com/j/([A-Za-z0-9]+)");
    private static final Pattern CAREERS_SUBDOMAIN = Pattern.compile("https?://([^./]+)\\.workable\\.com");

    /** Comma-separated Workable account slugs crawled by the ingestion; blank = pasted URLs only. */
    @Value("${application.job-copilot.workable.accounts:}")
    private String accountsCsv;

    public WorkableConnector(@Qualifier("atsRestClient") RestClient restClient, @Qualifier("atsObjectMapper") ObjectMapper objectMapper) {
        super(restClient, objectMapper);
    }

    // --- Payload ------------------------------------------------------------------------------

    record WorkableAccount(String name, List<WorkableJob> jobs) {}

    /** Snake_case on the wire; the flat city/state/country fields mirror {@code locations[0]}. */
    record WorkableJob(
        String title,
        String shortcode,
        @com.fasterxml.jackson.annotation.JsonProperty("employment_type") String employmentType,
        Boolean telecommuting,
        String department,
        String url,
        String shortlink,
        @com.fasterxml.jackson.annotation.JsonProperty("application_url") String applicationUrl,
        @com.fasterxml.jackson.annotation.JsonProperty("published_on") String publishedOn,
        @com.fasterxml.jackson.annotation.JsonProperty("created_at") String createdAt,
        String country,
        String city,
        String state,
        String experience,
        List<WorkableLocation> locations,
        String description
    ) {}

    record WorkableLocation(String country, String countryCode, String city, String region) {}

    // --- Connector ----------------------------------------------------------------------------

    @Override
    public boolean supports(String url) {
        return StringUtils.contains(url, "workable.com");
    }

    @Override
    public JobSource getSource() {
        return JobSource.WORKABLE;
    }

    @Override
    public PostingRef parseRef(String url) {
        if (url == null) {
            return null;
        }
        Matcher withAccount = ACCOUNT_POSTING.matcher(url);
        if (withAccount.find() && !"j".equals(withAccount.group(1))) {
            return new PostingRef(withAccount.group(1), withAccount.group(2));
        }
        Matcher direct = DIRECT_POSTING.matcher(url);
        return direct.find() ? new PostingRef(null, direct.group(1)) : null;
    }

    /** Board-based ATS: there is no cross-company search endpoint, so keyword search is a no-op. */
    @Override
    public List<JobOffer> fetchJobs(String keywords, String location) {
        return List.of();
    }

    @Override
    public List<JobOffer> fetchBySlug(String slug) {
        return StringUtils.isBlank(slug) ? List.of() : fetchFromAccount(slug);
    }

    @Override
    public List<JobOffer> fetchFromUrl(String url) {
        String account = accountOf(url);
        return StringUtils.isBlank(account) ? List.of() : fetchFromAccount(account);
    }

    /** Fetches every published job of the configured accounts (ingestion entry point). */
    public List<JobOffer> fetchConfiguredAccounts() {
        List<JobOffer> all = new ArrayList<>();
        csv(accountsCsv).forEach(account -> all.addAll(fetchFromAccount(account)));
        return all;
    }

    public List<JobOffer> fetchFromAccount(String account) {
        WorkableAccount payload = getJson(API_BASE + account + "?details=true", WorkableAccount.class);
        if (payload == null || payload.jobs() == null) {
            return List.of();
        }
        String companyName = StringUtils.defaultIfBlank(payload.name(), account);
        return payload
            .jobs()
            .stream()
            .filter(Objects::nonNull)
            .map(job -> mapJobOffer(job, companyName))
            .toList();
    }

    /**
     * Extracts the account slug from any Workable URL: the careers host ({@code acme.workable.com}),
     * the apply host ({@code apply.workable.com/acme/...}) or the API path itself.
     */
    private String accountOf(String url) {
        Matcher posting = ACCOUNT_POSTING.matcher(url);
        if (posting.find() && !"j".equals(posting.group(1))) {
            return posting.group(1);
        }
        if (StringUtils.contains(url, "/accounts/")) {
            return slugAfter(url, "/accounts/");
        }
        if (StringUtils.contains(url, "apply.workable.com/")) {
            String slug = slugAfter(url, "apply.workable.com/");
            return "j".equals(slug) ? "" : slug;
        }
        Matcher subdomain = CAREERS_SUBDOMAIN.matcher(url);
        boolean namedSubdomain = subdomain.find() && !StringUtils.equalsAny(subdomain.group(1), "apply", "www");
        return namedSubdomain ? subdomain.group(1) : "";
    }

    JobOffer mapJobOffer(WorkableJob job, String companyName) {
        JobOffer offer = new JobOffer();
        offer.setTitle(StringUtils.defaultString(job.title()));
        offer.setCompany(companyName);
        offer.setLocation(buildLocation(job));
        offer.setCountry(countryCode(job));
        offer.setDescription(stripHtml(job.description()));
        // application_url is the form itself; url/shortlink are the posting page.
        offer.setApplyUrl(StringUtils.firstNonBlank(job.applicationUrl(), job.url(), job.shortlink()));
        offer.setSourceId(StringUtils.trimToNull(job.shortcode()));
        offer.setSource(JobSource.WORKABLE);
        offer.setContractType(StringUtils.trimToNull(job.employmentType()));
        offer.setExperienceLevel(StringUtils.trimToNull(job.experience()));
        offer.setRemote(BooleanUtils.isTrue(job.telecommuting()));
        offer.setPublishedAt(AtsDates.parse(StringUtils.firstNonBlank(job.publishedOn(), job.createdAt())));
        return offer;
    }

    private String buildLocation(WorkableJob job) {
        String location = joinNonBlank(job.city(), job.country());
        if (StringUtils.isNotBlank(location)) {
            return location;
        }
        WorkableLocation first = firstLocation(job);
        return first == null ? null : StringUtils.trimToNull(joinNonBlank(first.city(), first.country()));
    }

    private String countryCode(WorkableJob job) {
        WorkableLocation first = firstLocation(job);
        return first == null ? null : StringUtils.trimToNull(first.countryCode());
    }

    private WorkableLocation firstLocation(WorkableJob job) {
        return job.locations() == null || job.locations().isEmpty() ? null : job.locations().getFirst();
    }

    private static String joinNonBlank(String... parts) {
        return StringUtils.trimToNull(StringUtils.join(java.util.Arrays.stream(parts).filter(StringUtils::isNotBlank).toArray(), ", "));
    }
}
