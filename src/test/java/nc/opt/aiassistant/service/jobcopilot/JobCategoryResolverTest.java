package nc.opt.aiassistant.service.jobcopilot;

import static org.assertj.core.api.Assertions.assertThat;

import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.domain.enumeration.JobSource;
import org.junit.jupiter.api.Test;

class JobCategoryResolverTest {

    private final SourceCategoryMapper mapper = new SourceCategoryMapper();
    private final JobCategoryResolver resolver = new JobCategoryResolver(mapper, new JobCategoryClassifier());

    private JobOffer offer(JobSource source, String sourceCategory, String title) {
        JobOffer offer = new JobOffer();
        offer.setSource(source);
        offer.setSourceCategory(sourceCategory);
        offer.setTitle(title);
        return offer;
    }

    @Test
    void theSourceTaxonomyWinsOverTheTitle() {
        // "Chargé de mission" tells you nothing; the ROME domaine M18 says information systems.
        JobOffer o = offer(JobSource.FRANCE_TRAVAIL, "M1805", "Chargé de mission");

        assertThat(resolver.resolve(o)).isEqualTo(JobCategories.TECH);
    }

    @Test
    void anUnmappedRomeDomaineFallsBackToItsGrandDomaine() {
        // F16 is not in the domaine table; F is "Construction, bâtiment et travaux publics".
        assertThat(mapper.map(JobSource.FRANCE_TRAVAIL, "F1601")).contains(JobCategories.CONSTRUCTION);
    }

    @Test
    void adzunaTagsAreMapped() {
        assertThat(mapper.map(JobSource.ADZUNA, "it-jobs")).contains(JobCategories.TECH);
        assertThat(mapper.map(JobSource.ADZUNA, "healthcare-nursing-jobs")).contains(JobCategories.HEALTH);
    }

    @Test
    void aSourceWithNoTaxonomyFallsBackToTheTitle() {
        JobOffer o = offer(JobSource.EMPLOI_NC, null, "Développeur Java");

        assertThat(mapper.map(JobSource.EMPLOI_NC, "whatever")).isEmpty();
        assertThat(resolver.resolve(o)).isEqualTo(JobCategories.TECH);
    }

    @Test
    void anUnknownSourceLabelDoesNotBlockTheTitleFallback() {
        JobOffer o = offer(JobSource.ADZUNA, "some-tag-adzuna-invented", "Infirmier");

        assertThat(resolver.resolve(o)).isEqualTo(JobCategories.HEALTH);
    }

    @Test
    void everyMappedValueStaysInsideTheVocabulary() {
        for (String rome : new String[] { "M1805", "C1101", "J1102", "N1103", "A1201", "L1203", "H2301", "I1304", "K2109" }) {
            assertThat(mapper.map(JobSource.FRANCE_TRAVAIL, rome)).isPresent().get().isIn(JobCategories.ALL);
        }
        for (String tag : new String[] { "it-jobs", "logistics-warehouse-jobs", "legal-jobs", "unknown" }) {
            assertThat(mapper.map(JobSource.ADZUNA, tag)).isPresent().get().isIn(JobCategories.ALL);
        }
    }

    @Test
    void aNullOfferIsOther() {
        assertThat(resolver.resolve(null)).isEqualTo(JobCategories.OTHER);
    }
}
