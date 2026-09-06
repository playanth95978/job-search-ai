package nc.opt.aiassistant.service.jobcopilot;

import static org.assertj.core.api.Assertions.assertThat;

import nc.opt.aiassistant.service.jobcopilot.QueryComplexity.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QueryComplexityTest {

    @ParameterizedTest
    @ValueSource(strings = { "java", "développeur java", "dev angular", "data engineer", "chef de projet" })
    void treatsAShortIntentAsSimple(String query) {
        assertThat(QueryComplexity.of(query)).isEqualTo(Level.SIMPLE);
    }

    /** Les mots vides ne doivent pas gonfler le décompte : « chef de projet » reste trois idées. */
    @Test
    void ignoresStopWordsWhenCounting() {
        assertThat(QueryComplexity.of("un poste de chef de projet")).isEqualTo(Level.SIMPLE);
    }

    @ParameterizedTest
    @ValueSource(strings = { "développeur java spring boot senior télétravail", "senior fullstack react node kubernetes aws" })
    void treatsAnAccumulationOfConstraintsAsComplex(String query) {
        assertThat(QueryComplexity.of(query)).isEqualTo(Level.COMPLEX);
    }

    /**
     * Une relation entre termes rend la requête complexe quelle que soit sa longueur : « java sans
     * astreinte » et « java astreinte » ont la même signature lexicale et le sens inverse.
     */
    @ParameterizedTest
    @ValueSource(strings = { "java sans astreinte", "dev remote but not freelance", "java ou kotlin" })
    void treatsARelationBetweenTermsAsComplex(String query) {
        assertThat(QueryComplexity.of(query)).isEqualTo(Level.COMPLEX);
    }

    @Test
    void treatsAQuestionAsComplex() {
        assertThat(QueryComplexity.of("quelles offres correspondent à mon profil ?")).isEqualTo(Level.COMPLEX);
    }

    /** Sans mots de l'utilisateur il n'y a rien à arbitrer : la recherche partira du profil. */
    @Test
    void treatsAnAbsentQueryAsSimple() {
        assertThat(QueryComplexity.of(null)).isEqualTo(Level.SIMPLE);
        assertThat(QueryComplexity.of("   ")).isEqualTo(Level.SIMPLE);
        assertThat(QueryComplexity.needsReranking(null)).isFalse();
    }

    @Test
    void needsRerankingMirrorsTheLevel() {
        assertThat(QueryComplexity.needsReranking("java")).isFalse();
        assertThat(QueryComplexity.needsReranking("java sans astreinte")).isTrue();
    }
}
