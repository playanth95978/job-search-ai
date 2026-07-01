package nc.opt.aiassistant.service.jobcopilot.geo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Deterministic behaviour of the GeoLocation agent (LLM fallback disabled). */
class GeoLocationServiceTest {

    private GeoLocationService geo;

    @BeforeEach
    void setUp() {
        geo = new GeoLocationService(null, false);
    }

    @Test
    void resolvesNewCaledoniaCityWithAccents() {
        GeoLocation g = geo.resolve("NOUMÉA");
        assertThat(g.known()).isTrue();
        assertThat(g.city()).isEqualTo("noumea");
        assertThat(g.region()).isEqualTo(GeoGazetteer.PROVINCE_SUD);
        assertThat(g.countryCode()).isEqualTo("NC");
    }

    @Test
    void exactCityScoresHighest() {
        GeoScore s = geo.score(geo.resolve("Nouméa"), geo.resolve("Nouméa, Nouvelle-Calédonie"));
        assertThat(s.level()).isEqualTo(GeoMatchLevel.EXACT);
        assertThat(s.score()).isEqualTo(1.0);
    }

    @Test
    void sameGrandNoumeaAgglomerationScoresHigh() {
        GeoScore s = geo.score(geo.resolve("Nouméa"), geo.resolve("Païta"));
        assertThat(s.level()).isEqualTo(GeoMatchLevel.AGGLOMERATION);
        assertThat(s.score()).isEqualTo(0.9);
    }

    @Test
    void sameProvinceScoresAsRegion() {
        // Reference is province-only; Bourail is in Province Sud.
        GeoScore s = geo.score(geo.resolve("Province Sud"), geo.resolve("Bourail"));
        assertThat(s.level()).isEqualTo(GeoMatchLevel.REGION);
    }

    @Test
    void sameCountryDifferentProvinceScoresAsCountry() {
        GeoScore s = geo.score(geo.resolve("Nouméa"), geo.resolve("Koné"));
        assertThat(s.level()).isEqualTo(GeoMatchLevel.COUNTRY);
        assertThat(s.score()).isEqualTo(0.5);
    }

    @Test
    void differentCountryIsMismatch() {
        GeoScore s = geo.score(geo.resolve("Nouméa"), geo.resolve("Sydney, Australia"));
        assertThat(s.level()).isEqualTo(GeoMatchLevel.MISMATCH);
    }

    @Test
    void remoteOfferStaysRelevant() {
        GeoScore s = geo.score(geo.resolve("Nouméa"), geo.resolve("Remote"));
        assertThat(s.level()).isEqualTo(GeoMatchLevel.REMOTE);
        assertThat(s.score()).isGreaterThanOrEqualTo(0.85);
    }

    @Test
    void noUsableReferenceIsNeutral() {
        GeoScore s = geo.score(geo.resolve(""), geo.resolve("Sydney"));
        assertThat(s.score()).isEqualTo(0.5);
        assertThat(s.level()).isEqualTo(GeoMatchLevel.UNKNOWN);
    }

    @Test
    void sameFrenchRegionScoresAsRegion() {
        // Lyon and Grenoble are both in Auvergne-Rhône-Alpes.
        GeoScore s = geo.score(geo.resolve("Lyon"), geo.resolve("Grenoble"));
        assertThat(s.level()).isEqualTo(GeoMatchLevel.REGION);
    }

    @Test
    void sameCountryDifferentFrenchRegionScoresAsCountry() {
        GeoScore s = geo.score(geo.resolve("Paris"), geo.resolve("Marseille"));
        assertThat(s.level()).isEqualTo(GeoMatchLevel.COUNTRY);
    }

    @Test
    void sameAustralianStateScoresAsRegion() {
        GeoScore s = geo.score(geo.resolve("Sydney"), geo.resolve("Newcastle"));
        assertThat(s.level()).isEqualTo(GeoMatchLevel.REGION);
    }

    @Test
    void unknownOfferLocationIsLowNeutral() {
        GeoScore s = geo.score(geo.resolve("Nouméa"), geo.resolve("Zzzville"));
        assertThat(s.level()).isEqualTo(GeoMatchLevel.UNKNOWN);
        assertThat(s.score()).isEqualTo(0.4);
    }
}
