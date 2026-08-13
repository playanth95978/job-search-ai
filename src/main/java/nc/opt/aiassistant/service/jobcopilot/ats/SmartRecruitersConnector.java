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
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Connecteur SmartRecruiters, en <b>lecture seule</b> : il lit les offres publiées d'un board
 * public. La soumission de candidature via l'API Posting a été retirée — postuler au nom du
 * candidat sans son geste explicite sort du cadre des CGU de l'ATS.
 */
@Slf4j
@Component
public final class SmartRecruitersConnector extends AbstractAtsConnector {

    private static final String API_BASE = "https://api.smartrecruiters.com/v1/companies";
    private static final String POSTING_BASE = "https://jobs.smartrecruiters.com/";
    /** SmartRecruiters posting URL: jobs.smartrecruiters.com/{companyId}/{postingId}[-slug]. */
    private static final Pattern URL_REF = Pattern.compile("smartrecruiters\\.com/([^/]+)/(\\d+)");

    public SmartRecruitersConnector(
        @Qualifier("atsRestClient") RestClient restClient,
        @Qualifier("atsObjectMapper") ObjectMapper objectMapper
    ) {
        super(restClient, objectMapper);
    }

    // --- Payload ------------------------------------------------------------------------------

    record SmartRecruitersPostings(List<SmartRecruitersPosting> content) {}

    record SmartRecruitersPosting(
        String id,
        String name,
        String releasedDate,
        SmartRecruitersLocation location,
        SmartRecruitersTypeOfEmployment typeOfEmployment
    ) {}

    record SmartRecruitersLocation(String city, String region, String country, Boolean remote) {}

    record SmartRecruitersTypeOfEmployment(String label) {}

    // --- Connector ----------------------------------------------------------------------------

    @Override
    public boolean supports(String url) {
        return StringUtils.contains(url, "smartrecruiters.com");
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
        String companyId = ref != null ? ref.site() : slugAfter(url, "smartrecruiters.com/");
        return StringUtils.isBlank(companyId) ? List.of() : fetchFromCompany(companyId);
    }

    public List<JobOffer> fetchFromCompany(String companyId) {
        SmartRecruitersPostings payload = getJson(API_BASE + "/" + companyId + "/postings", SmartRecruitersPostings.class);
        if (payload == null || payload.content() == null) {
            return List.of();
        }
        return payload
            .content()
            .stream()
            .filter(Objects::nonNull)
            .map(posting -> mapJobOffer(posting, companyId))
            .toList();
    }

    JobOffer mapJobOffer(SmartRecruitersPosting posting, String companyId) {
        JobOffer offer = new JobOffer();
        offer.setTitle(StringUtils.defaultString(posting.name()));
        offer.setCompany(companyId);
        offer.setSourceId(StringUtils.trimToNull(posting.id()));
        offer.setSource(JobSource.SMARTRECRUITERS);
        offer.setApplyUrl(POSTING_BASE + companyId + "/" + StringUtils.defaultString(posting.id()));
        offer.setPublishedAt(AtsDates.parse(posting.releasedDate()));
        if (posting.location() != null) {
            offer.setLocation(StringUtils.trimToNull(posting.location().city()));
            offer.setCountry(StringUtils.trimToNull(posting.location().country()));
            offer.setRemote(BooleanUtils.isTrue(posting.location().remote()));
        }
        if (posting.typeOfEmployment() != null) {
            offer.setContractType(StringUtils.trimToNull(posting.typeOfEmployment().label()));
        }
        return offer;
    }
}
