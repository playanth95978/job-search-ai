package nc.opt.aiassistant.service.reranker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/**
 * Verrouille les métriques de fidélité, qui décident du choix d'un modèle de reranking : une
 * corrélation fausse ferait retenir un modèle qui classe mal.
 */
class RankingFidelityTest {

    @Test
    void spearmanIsOneForIdenticalOrderAndMinusOneForReversed() {
        float[] ascending = { 1f, 2f, 3f, 4f, 5f };
        float[] shiftedButSameOrder = { 10f, 20f, 30f, 40f, 50f };
        float[] reversed = { 5f, 4f, 3f, 2f, 1f };

        // Seul l'ordre compte : deux modèles n'ont pas la même échelle de scores.
        assertThat(RankingFidelity.spearman(ascending, shiftedButSameOrder)).isCloseTo(1.0, within(1e-9));
        assertThat(RankingFidelity.spearman(ascending, reversed)).isCloseTo(-1.0, within(1e-9));
    }

    /**
     * Cas qui motivait le passage à Commons Math : avec des ex æquo, la formule abrégée
     * {@code 1 - 6*Σd²/(n(n²-1))} n'est plus exacte. Ici les deux séries ont un ordre identique —
     * la corrélation vaut donc exactement 1, ce que l'ancienne implémentation ne trouvait pas.
     */
    @Test
    void spearmanHandlesTiedScores() {
        float[] withTies = { 1f, 2f, 2f, 3f };
        float[] sameOrderWithTies = { 10f, 20f, 20f, 30f };

        assertThat(RankingFidelity.spearman(withTies, sameOrderWithTies)).isCloseTo(1.0, within(1e-9));
    }

    /** Un modèle qui donne le même score à tout n'a pas de classement : rien à corréler. */
    @Test
    void spearmanIsNotANumberForConstantScores() {
        float[] constant = { 2f, 2f, 2f, 2f };
        float[] varied = { 1f, 2f, 3f, 4f };

        assertThat(RankingFidelity.spearman(constant, varied)).isNaN();
    }

    @Test
    void rankingSortsByDescendingScore() {
        assertThat(RankingFidelity.ranking(new float[] { 0.2f, 0.9f, 0.5f })).containsExactly(1, 2, 0);
    }

    @Test
    void topKOverlapCountsSharedDocumentsRegardlessOfTheirOrder() {
        int[] reference = { 0, 1, 2, 3, 4 };
        int[] sameTopThreeReordered = { 2, 0, 1, 9, 8 };
        int[] disjointTopThree = { 7, 8, 9, 0, 1 };

        assertThat(RankingFidelity.topKOverlap(reference, sameTopThreeReordered, 3)).isEqualTo(1.0);
        assertThat(RankingFidelity.topKOverlap(reference, disjointTopThree, 3)).isEqualTo(0.0);
    }

    @Test
    void top1AgreementComparesOnlyTheBestDocument() {
        assertThat(RankingFidelity.top1Agreement(new int[] { 4, 1 }, new int[] { 4, 9 })).isEqualTo(1.0);
        assertThat(RankingFidelity.top1Agreement(new int[] { 4, 1 }, new int[] { 1, 4 })).isEqualTo(0.0);
    }
}
