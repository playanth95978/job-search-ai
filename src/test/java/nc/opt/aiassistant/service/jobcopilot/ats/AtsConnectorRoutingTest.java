package nc.opt.aiassistant.service.jobcopilot.ats;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import nc.opt.aiassistant.service.jobcopilot.ats.AtsConnector.PostingRef;
import org.junit.jupiter.api.Test;

/**
 * Couvre l'extraction de la référence de publication depuis une URL d'ATS, qui sert à récupérer le
 * détail d'une offre — y compris quand l'offre vient d'un agrégateur mais pointe vers un board ATS.
 *
 * <p>Les connecteurs sont en lecture seule : aucune candidature n'est soumise par l'application.</p>
 */
class AtsConnectorRoutingTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void leverParsesSiteAndPostingIdFromApplyUrl() {
        LeverConnector lever = new LeverConnector(mapper);
        String url = "https://jobs.lever.co/acme/12345678-1234-1234-1234-123456789abc/apply";

        PostingRef ref = lever.parseRef(url);
        assertThat(ref).isNotNull();
        assertThat(ref.site()).isEqualTo("acme");
        assertThat(ref.postingId()).isEqualTo("12345678-1234-1234-1234-123456789abc");

        assertThat(lever.supports(url)).isTrue();
        assertThat(lever.supports("https://example.com/jobs/1")).isFalse();
    }

    @Test
    void smartRecruitersParsesCompanyAndPostingId() {
        SmartRecruitersConnector sr = new SmartRecruitersConnector(mapper);
        String url = "https://jobs.smartrecruiters.com/AcmeInc/743999912345678";

        assertThat(sr.parseRef(url).site()).isEqualTo("AcmeInc");
        assertThat(sr.parseRef(url).postingId()).isEqualTo("743999912345678");
    }

    @Test
    void greenhouseParsesBothBoardAndQueryParamUrls() {
        GreenhouseConnector gh = new GreenhouseConnector(mapper);

        assertThat(gh.parseRef("https://boards.greenhouse.io/acme/jobs/456").postingId()).isEqualTo("456");
        assertThat(gh.parseRef("https://acme.com/careers?gh_jid=789").postingId()).isEqualTo("789");
    }
}
