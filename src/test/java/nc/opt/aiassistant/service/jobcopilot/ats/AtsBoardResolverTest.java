package nc.opt.aiassistant.service.jobcopilot.ats;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.domain.enumeration.JobSource;
import org.junit.jupiter.api.Test;

/**
 * Le résolveur est le cœur « déterministe » de la découverte par nom d'entreprise : un slug n'est
 * retenu que si un board répond réellement des offres. Les connecteurs sont ici des doublures, on
 * teste la logique de candidature et de sélection, pas le réseau.
 */
class AtsBoardResolverTest {

    /** Doublure de connecteur board-based : répond uniquement pour le slug qu'on lui donne. */
    private static final class FakeBoardConnector implements AtsConnector {

        private final JobSource source;
        private final String knownSlug;
        private final List<String> probedSlugs = new java.util.ArrayList<>();

        FakeBoardConnector(JobSource source, String knownSlug) {
            this.source = source;
            this.knownSlug = knownSlug;
        }

        @Override
        public boolean supports(String url) {
            return false;
        }

        @Override
        public JobSource getSource() {
            return source;
        }

        @Override
        public List<JobOffer> fetchJobs(String keywords, String location) {
            return List.of();
        }

        @Override
        public List<JobOffer> fetchBySlug(String slug) {
            probedSlugs.add(slug);
            if (!slug.equals(knownSlug)) {
                return List.of();
            }
            JobOffer offer = new JobOffer();
            offer.setTitle("Senior Engineer");
            offer.setCompany(slug);
            offer.setSource(source);
            return List.of(offer);
        }
    }

    /** Connecteur d'agrégateur : pas de notion de slug, il ne doit jamais être sondé. */
    private static final class FakeAggregatorConnector implements AtsConnector {

        private boolean probed;

        @Override
        public boolean supports(String url) {
            return false;
        }

        @Override
        public JobSource getSource() {
            return JobSource.CAREERJET;
        }

        @Override
        public List<JobOffer> fetchJobs(String keywords, String location) {
            probed = true;
            return List.of();
        }
    }

    @Test
    void resolvesACompanyNameToTheAtsThatActuallyAnswers() {
        FakeBoardConnector ashby = new FakeBoardConnector(JobSource.ASHBY, "never-matches");
        FakeBoardConnector workable = new FakeBoardConnector(JobSource.WORKABLE, "huggingface");
        AtsBoardResolver resolver = new AtsBoardResolver(List.of(ashby, workable));

        Optional<AtsBoardResolver.ResolvedBoard> resolved = resolver.resolve("Hugging Face");

        assertThat(resolved).isPresent();
        assertThat(resolved.get().source()).isEqualTo(JobSource.WORKABLE);
        assertThat(resolved.get().slug()).isEqualTo("huggingface");
        assertThat(resolved.get().offers()).hasSize(1);
        // Les deux ATS sont sondés en parallèle sur le même candidat : c'est la seule façon de savoir
        // lequel héberge l'entreprise.
        assertThat(ashby.probedSlugs).contains("huggingface");
    }

    @Test
    void triesTheHyphenatedConventionWhenTheCompactOneIsUnknown() {
        FakeBoardConnector recruitee = new FakeBoardConnector(JobSource.RECRUITEE, "hugging-face");
        AtsBoardResolver resolver = new AtsBoardResolver(List.of(recruitee));

        Optional<AtsBoardResolver.ResolvedBoard> resolved = resolver.resolve("Hugging Face");

        assertThat(resolved).isPresent();
        assertThat(resolved.get().slug()).isEqualTo("hugging-face");
        assertThat(recruitee.probedSlugs).containsExactly("huggingface", "hugging-face");
    }

    @Test
    void returnsEmptyRatherThanGuessingWhenNoBoardAnswers() {
        FakeBoardConnector lever = new FakeBoardConnector(JobSource.LEVER, "someone-else");
        AtsBoardResolver resolver = new AtsBoardResolver(List.of(lever));

        assertThat(resolver.resolve("Entreprise Inconnue")).isEmpty();
    }

    @Test
    void neverProbesConnectorsThatHaveNoBoardNotion() {
        FakeAggregatorConnector aggregator = new FakeAggregatorConnector();
        AtsBoardResolver resolver = new AtsBoardResolver(List.of(aggregator));

        assertThat(resolver.resolve("ACME")).isEmpty();
        assertThat(aggregator.probed).isFalse();
    }

    @Test
    void buildsSlugCandidatesShortestFirstAndStripsAccentsAndLegalSuffixes() {
        AtsBoardResolver resolver = new AtsBoardResolver(List.of());

        assertThat(resolver.slugCandidates("Société Générale")).containsExactly("societegenerale", "societe-generale", "societe generale");
        assertThat(resolver.slugCandidates("ACME Inc")).startsWith("acme").contains("acmeinc");
    }

    @Test
    void keepsSupportingAPastedBoardUrl() {
        AtsBoardResolver resolver = new AtsBoardResolver(List.of(new FakeBoardConnector(JobSource.ASHBY, "acme")));

        // Aucun connecteur ne "supporte" cette URL dans les doublures : le résolveur ne doit pas pour
        // autant retomber sur la découverte par slug, au risque de sonder une URL entière.
        assertThat(resolver.resolve("https://jobs.ashbyhq.com/acme")).isEmpty();
    }
}
