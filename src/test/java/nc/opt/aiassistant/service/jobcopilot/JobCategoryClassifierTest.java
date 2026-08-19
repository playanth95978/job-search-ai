package nc.opt.aiassistant.service.jobcopilot;

import static org.assertj.core.api.Assertions.assertThat;

import nc.opt.aiassistant.domain.JobOffer;
import org.junit.jupiter.api.Test;

class JobCategoryClassifierTest {

    private final JobCategoryClassifier classifier = new JobCategoryClassifier();

    @Test
    void infersTheFamilyFromTheTitle() {
        assertThat(classifier.classify("Développeur Java Spring Boot")).isEqualTo("tech");
        assertThat(classifier.classify("Data Analyst")).isEqualTo("data");
        assertThat(classifier.classify("Infirmier de nuit")).isEqualTo("health");
        assertThat(classifier.classify("Conducteur de travaux")).isEqualTo("construction");
        assertThat(classifier.classify("Chef de produit digital")).isEqualTo("product");
    }

    @Test
    void ignoresAccentsAndCase() {
        assertThat(classifier.classify("INGÉNIEUR SYSTÈME")).isEqualTo("tech");
        assertThat(classifier.classify("ingenieur systeme")).isEqualTo("tech");
    }

    @Test
    void prefersTheMoreSpecificFamilyWhenTwoCouldMatch() {
        // "Data engineer" hits both the data and tech groups; data is declared first and wins.
        assertThat(classifier.classify("Data Engineer")).isEqualTo("data");
    }

    @Test
    void fallsBackToOtherRatherThanGuessing() {
        assertThat(classifier.classify("Poste polyvalent")).isEqualTo("other");
        assertThat(classifier.classify("")).isEqualTo("other");
        assertThat(classifier.classify((String) null)).isEqualTo("other");
    }

    @Test
    void classifiesAnOfferThroughItsTitle() {
        JobOffer offer = new JobOffer();
        offer.setTitle("Comptable unique");

        assertThat(classifier.classify(offer)).isEqualTo("finance");
        assertThat(classifier.classify((JobOffer) null)).isEqualTo("other");
    }
}
