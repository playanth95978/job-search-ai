package nc.opt.aiassistant.service.jobcopilot.ats;

import static org.assertj.core.api.Assumptions.assumeThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * Résolution RÉELLE d'un nom d'entreprise vers son board ATS : sonde les six API publiques et affiche
 * ce qui répond. Ne tourne qu'avec {@code -Dats.live=true}, donc inerte en CI.
 *
 * <pre>mvn test -Dtest=AtsBoardResolverLiveTest -Dats.live=true -Dats.companies="Hugging Face,Linear"</pre>
 */
class AtsBoardResolverLiveTest {

    private static final String DEFAULT_COMPANIES = "Hugging Face,Linear,Ashby";

    @Test
    void resolvesRealCompanies() {
        assumeThat(System.getProperty("ats.live")).as("ats.live").isEqualTo("true");

        RestClient restClient = RestClient.create();
        ObjectMapper mapper = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        AtsBoardResolver resolver = new AtsBoardResolver(
            List.of(
                new GreenhouseConnector(restClient, mapper),
                new LeverConnector(restClient, mapper),
                new SmartRecruitersConnector(restClient, mapper),
                new AshbyConnector(restClient, mapper),
                new WorkableConnector(restClient, mapper),
                new RecruiteeConnector(restClient, mapper)
            )
        );

        for (String company : System.getProperty("ats.companies", DEFAULT_COMPANIES).split(",")) {
            Optional<AtsBoardResolver.ResolvedBoard> resolved = resolver.resolve(company.trim());
            System.out.println(
                resolved
                    .map(board ->
                        "%-16s -> %-16s slug=%-20s %d offre(s) | ex: %s".formatted(
                            company.trim(),
                            board.source(),
                            board.slug(),
                            board.offers().size(),
                            board.offers().isEmpty() ? "-" : board.offers().getFirst().getTitle()
                        )
                    )
                    .orElse("%-16s -> aucun board public trouvé".formatted(company.trim()))
            );
        }
    }
}
