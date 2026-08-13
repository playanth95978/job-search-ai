package nc.opt.aiassistant.service.jobcopilot.ats;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.domain.enumeration.JobSource;
import nc.opt.aiassistant.service.jobcopilot.ats.support.AtsDates;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Connector for the Lever postings API: {@code GET https://api.lever.co/v0/postings/{company}?mode=json},
 * public, one feed per company.
 */
@Slf4j
@Component
public final class LeverConnector extends AbstractAtsConnector {

    private static final String API_BASE = "https://api.lever.co/v0/postings";
    /** Lever posting URL: jobs.lever.co/{site}/{postingId} (postingId is a UUID), optionally /apply. */
    private static final Pattern URL_REF = Pattern.compile("lever\\.co/([^/]+)/([0-9a-fA-F-]{36})");

    public LeverConnector(@Qualifier("atsRestClient") RestClient restClient, @Qualifier("atsObjectMapper") ObjectMapper objectMapper) {
        super(restClient, objectMapper);
    }

    // --- Payload ------------------------------------------------------------------------------

    record LeverPosting(
        String id,
        String text,
        String descriptionPlain,
        String description,
        String applyUrl,
        String hostedUrl,
        Long createdAt,
        LeverCategories categories
    ) {}

    record LeverCategories(String location, String commitment, String team, String department) {}

    // --- Connector ----------------------------------------------------------------------------

    @Override
    public boolean supports(String url) {
        return StringUtils.contains(url, "lever.co");
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
        String company = slugAfter(url, "lever.co/");
        return StringUtils.isBlank(company) ? List.of() : fetchFromCompany(company);
    }

    public List<JobOffer> fetchFromCompany(String company) {
        LeverPosting[] postings = getJson(API_BASE + "/" + company + "?mode=json", LeverPosting[].class);
        if (postings == null) {
            return List.of();
        }
        return java.util.Arrays.stream(postings)
            .filter(Objects::nonNull)
            .map(posting -> mapJobOffer(posting, company))
            .toList();
    }

    JobOffer mapJobOffer(LeverPosting posting, String company) {
        JobOffer offer = new JobOffer();
        offer.setTitle(StringUtils.defaultString(posting.text()));
        offer.setCompany(company);
        offer.setDescription(StringUtils.firstNonBlank(posting.descriptionPlain(), stripHtml(posting.description())));
        offer.setApplyUrl(StringUtils.firstNonBlank(posting.applyUrl(), posting.hostedUrl()));
        offer.setSourceId(StringUtils.trimToNull(posting.id()));
        offer.setSource(JobSource.LEVER);
        // createdAt is epoch milliseconds.
        offer.setPublishedAt(posting.createdAt() == null ? null : AtsDates.fromEpoch(posting.createdAt()));
        if (posting.categories() != null) {
            offer.setLocation(StringUtils.trimToNull(posting.categories().location()));
            offer.setContractType(StringUtils.trimToNull(posting.categories().commitment()));
        }
        offer.setRemote(detectRemote(offer.getLocation(), offer.getTitle()));
        return offer;
    }
}
