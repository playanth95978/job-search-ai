package nc.opt.aiassistant.service.jobcopilot.ats;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.List;
import nc.opt.aiassistant.domain.JobOffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

/**
 * Couvre la prise en compte des critères de l'écran de recherche — mots-clés multiples et
 * localisation — pour les sources qui les traitent elles-mêmes.
 *
 * <p>Ces trois cas étaient cassés : chercher « java spring » ne remontait rien sur les flux filtrés
 * côté client, et France Travail recevait une ville dans un paramètre qui n'accepte qu'un code
 * postal.</p>
 */
class SearchCriteriaHandlingTest {

    private final ObjectMapper mapper = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    private final RestClient restClient = RestClient.create();

    // --- Filtrage par mots-clés côté client ---------------------------------------------------

    private final RemoteOkConnector remoteOk = new RemoteOkConnector(restClient, mapper);

    private RemoteOkConnector.RemoteOkJob job(String title, String company, String description) {
        return new RemoteOkConnector.RemoteOkJob("1", title, company, "Remote", description, "https://remoteok.com/l/1", null, List.of());
    }

    /** {@code protected static} dans la base : accessible directement depuis le même paquet. */
    private boolean matches(String keywords, String title, String company, String description) {
        RemoteOkConnector.RemoteOkJob candidate = job(title, company, description);
        return AbstractAtsConnector.matchesAllKeywords(keywords, candidate.position(), candidate.company(), candidate.description());
    }

    @Test
    void multiWordSearchMatchesWhenEveryWordIsPresentAcrossTheOffer() {
        // Le cas qui ne remontait rien : les deux mots existent, mais jamais côte à côte dans le titre.
        assertThat(matches("java spring", "Senior Backend Engineer", "ACME", "Java 21, Spring Boot, PostgreSQL")).isTrue();
        assertThat(matches("java angular", "Fullstack Java Developer", "ACME", "Angular 20 front-end")).isTrue();
    }

    @Test
    void multiWordSearchStillExcludesOffersMissingOneWord() {
        assertThat(matches("java kotlin", "Senior Backend Engineer", "ACME", "Java 21, Spring Boot")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "   " })
    void blankKeywordsKeepEverything(String keywords) {
        assertThat(matches(keywords, "Anything", "ACME", "")).isTrue();
    }

    @Test
    void keywordMatchingIsCaseInsensitiveAndLooksBeyondTheTitle() {
        assertThat(matches("ACME", "Developer", "Acme Corp", "")).isTrue();
        assertThat(matches("postgresql", "Developer", "ACME", "We run PostgreSQL 17")).isTrue();
    }

    // --- France Travail : mots-clés et localisation --------------------------------------------

    private final FranceTravailConnector franceTravail = new FranceTravailConnector(restClient, mapper);

    @ParameterizedTest
    @CsvSource({
        // L'API attend des virgules : avec des espaces elle cherche l'expression entière.
        "java spring boot, 'java,spring,boot'",
        "développeur, développeur",
        "'  java   angular  ', 'java,angular'",
        // Plafonné à 5 mots-clés, au-delà l'API rejette la requête.
        "a b c d e f g, 'a,b,c,d,e'",
    })
    void splitsKeywordsOnWhitespaceAsTheApiExpects(String input, String expected) {
        assertThat((String) ReflectionTestUtils.invokeMethod(franceTravail, "toMotsCles", input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({ "98800, true", "75001, true", "Paris, false", "Nouméa, false", "750, false", "750011, false" })
    void onlyForwardsALocationThatIsActuallyAPostalCode(String location, boolean expected) {
        // « Paris » dans codePostal provoquait un 400 côté API, donc zéro résultat pour la source.
        assertThat((boolean) ReflectionTestUtils.invokeMethod(franceTravail, "isPostalCode", location)).isEqualTo(expected);
    }

    // --- Jobicy : la localisation ne doit jamais vider une source remote-only ------------------

    private final JobicyConnector jobicy = new JobicyConnector(restClient, mapper);

    @Test
    void jobicyKeepsWorldwideOffersWhateverTheSearchedRegion() {
        JobOffer worldwide = new JobOffer();
        worldwide.setLocation("Anywhere");
        JobOffer europe = new JobOffer();
        europe.setLocation("Europe");

        assertThat((boolean) ReflectionTestUtils.invokeMethod(jobicy, "matchesGeo", worldwide, "apac")).isTrue();
        assertThat((boolean) ReflectionTestUtils.invokeMethod(jobicy, "matchesGeo", europe, "apac")).isFalse();
        // Une ville qui ne correspond à aucune région d'éligibilité ne doit rien filtrer.
        assertThat((String) ReflectionTestUtils.invokeMethod(jobicy, "geoFor", "Nouméa")).isNull();
        assertThat((String) ReflectionTestUtils.invokeMethod(jobicy, "geoFor", "Brisbane")).isEqualTo("apac");
    }

    /** L'écran envoie désormais les codes de zone du sélecteur : chacun doit être reconnu tel quel. */
    @ParameterizedTest
    @ValueSource(strings = { "europe", "apac", "usa", "canada", "latam", "emea" })
    void jobicyAcceptsEveryRegionCodeOfferedByTheSearchForm(String region) {
        assertThat((String) ReflectionTestUtils.invokeMethod(jobicy, "geoFor", region)).isEqualTo(region);
    }
}
