package nc.opt.aiassistant.service.jobcopilot.ats;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import nc.opt.aiassistant.config.ApplicationProperties;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.domain.enumeration.JobSource;
import nc.opt.aiassistant.service.jobcopilot.ats.AtsConnector.PostingRef;
import nc.opt.aiassistant.service.pdf.ResumePdfService;
import org.junit.jupiter.api.Test;

class AtsConnectorRoutingTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ResumePdfService pdf = new ResumePdfService();

    private JobOffer offer(JobSource source, String applyUrl) {
        JobOffer o = new JobOffer();
        o.setSource(source);
        o.setApplyUrl(applyUrl);
        return o;
    }

    @Test
    void leverParsesRefAndAutoAppliesByUrlEvenForAggregatorSource() {
        LeverConnector lever = new LeverConnector(mapper, pdf);
        String url = "https://jobs.lever.co/acme/12345678-1234-1234-1234-123456789abc/apply";

        PostingRef ref = lever.parseRef(url);
        assertThat(ref).isNotNull();
        assertThat(ref.site()).isEqualTo("acme");
        assertThat(ref.postingId()).isEqualTo("12345678-1234-1234-1234-123456789abc");

        // An offer sourced from an aggregator but linking to a Lever board is still auto-appliable.
        assertThat(lever.canAutoApply(offer(JobSource.FRANCE_TRAVAIL, url))).isTrue();
        assertThat(lever.canAutoApply(offer(JobSource.ADZUNA, "https://example.com/jobs/1"))).isFalse();
    }

    @Test
    void smartRecruitersAutoApplyIsGatedByConfig() {
        ApplicationProperties props = new ApplicationProperties();
        SmartRecruitersConnector sr = new SmartRecruitersConnector(mapper, pdf, props);
        String url = "https://jobs.smartrecruiters.com/AcmeInc/743999912345678";

        assertThat(sr.parseRef(url).site()).isEqualTo("AcmeInc");
        assertThat(sr.parseRef(url).postingId()).isEqualTo("743999912345678");

        // Off by default → not capable even for a SmartRecruiters URL.
        assertThat(sr.supportsAutoApply()).isFalse();
        assertThat(sr.canAutoApply(offer(JobSource.ADZUNA, url))).isFalse();

        props.getJobCopilot().getSmartRecruiters().setAutoApplyEnabled(true);
        assertThat(sr.supportsAutoApply()).isTrue();
        assertThat(sr.canAutoApply(offer(JobSource.ADZUNA, url))).isTrue();
    }

    @Test
    void greenhouseParsesRefButDoesNotAutoApply() {
        GreenhouseConnector gh = new GreenhouseConnector(mapper);

        assertThat(gh.parseRef("https://boards.greenhouse.io/acme/jobs/456").postingId()).isEqualTo("456");
        assertThat(gh.parseRef("https://acme.com/careers?gh_jid=789").postingId()).isEqualTo("789");

        // Auto-apply needs a per-employer Job Board API key → always assisted fallback.
        assertThat(gh.supportsAutoApply()).isFalse();
        assertThat(gh.canAutoApply(offer(JobSource.GREENHOUSE, "https://boards.greenhouse.io/acme/jobs/456"))).isFalse();
    }
}
