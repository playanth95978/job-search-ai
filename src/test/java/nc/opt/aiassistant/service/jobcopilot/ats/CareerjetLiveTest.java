package nc.opt.aiassistant.service.jobcopilot.ats;

import static org.assertj.core.api.Assumptions.assumeThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import nc.opt.aiassistant.domain.JobOffer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Appel RÉEL de l'API Careerjet, de bout en bout : requête HTTP + auth Basic + mapping vers
 * {@link JobOffer}. Ne tourne que si les identifiants sont fournis en propriétés système, donc
 * inerte en CI.
 *
 * <pre>mvn test -Dtest=CareerjetLiveTest -Dcareerjet.key=... -Dcareerjet.ip=...</pre>
 */
class CareerjetLiveTest {

    @Test
    void fetchesRealJobs() {
        String key = System.getProperty("careerjet.key");
        String ip = System.getProperty("careerjet.ip");
        assumeThat(key).as("careerjet.key").isNotBlank();
        assumeThat(ip).as("careerjet.ip").isNotBlank();

        CareerjetConnector connector = new CareerjetConnector(new ObjectMapper());
        ReflectionTestUtils.setField(connector, "apiKey", key);
        ReflectionTestUtils.setField(connector, "userIp", ip);
        ReflectionTestUtils.setField(connector, "localeCode", System.getProperty("careerjet.locale", "fr_FR"));
        ReflectionTestUtils.setField(connector, "userAgent", "Mozilla/5.0 (compatible; JobSearchAI/1.0)");
        ReflectionTestUtils.setField(connector, "referer", System.getProperty("careerjet.referer", "https://aicarreerfinder.com"));
        ReflectionTestUtils.setField(connector, "pageSize", 5);
        ReflectionTestUtils.setField(connector, "maxPages", 1);

        List<JobOffer> offers = connector.fetchJobs(
            System.getProperty("careerjet.keywords", "java"),
            System.getProperty("careerjet.where", "Paris")
        );

        System.out.println("=== Careerjet : " + offers.size() + " offre(s) ===");
        offers.forEach(o ->
            System.out.println(
                """
                ---
                titre      : %s
                entreprise : %s
                lieu       : %s / pays %s
                remote     : %s
                salaire    : %s - %s %s
                publiée le : %s
                url        : %s
                source     : %s (id %s)
                description: %s
                """.formatted(
                    o.getTitle(),
                    o.getCompany(),
                    o.getLocation(),
                    o.getCountry(),
                    o.getRemote(),
                    o.getSalaryMin(),
                    o.getSalaryMax(),
                    o.getSalaryCurrency(),
                    o.getPublishedAt(),
                    o.getApplyUrl(),
                    o.getSource(),
                    o.getSourceId(),
                    abbreviate(o.getDescription())
                )
            )
        );
    }

    private static String abbreviate(String text) {
        if (text == null) return null;
        return text.length() <= 160 ? text : text.substring(0, 160) + "…";
    }
}
