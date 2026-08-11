package nc.opt.aiassistant.service.reranker;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.math3.stat.correlation.SpearmansCorrelation;
import org.apache.commons.math3.stat.ranking.NaNStrategy;
import org.apache.commons.math3.stat.ranking.NaturalRanking;
import org.apache.commons.math3.stat.ranking.TiesStrategy;

/**
 * Compare deux classements produits sur les mêmes documents.
 *
 * <p>Un reranker n'a de valeur que par l'ordre qu'il produit : un modèle plus rapide qui déplace le
 * bon résultat de la 1re à la 8e place est une régression, quel que soit son gain de vitesse. Ces
 * métriques servent donc de critère d'acceptation, la vitesse n'étant qu'un budget.</p>
 *
 * <p>On mesure à la fois le sommet du classement (top-1/5/10, ce que l'utilisateur voit) et l'ordre
 * global (corrélation de rangs) : un modèle peut préserver le podium tout en désordonnant la
 * suite, ou l'inverse.</p>
 */
final class RankingFidelity {

    private RankingFidelity() {}

    /** Indices des documents triés par score décroissant. */
    static int[] ranking(float[] scores) {
        List<Integer> order = new ArrayList<>(scores.length);
        for (int i = 0; i < scores.length; i++) {
            order.add(i);
        }
        order.sort((a, b) -> Float.compare(scores[b], scores[a]));
        int[] result = new int[scores.length];
        for (int i = 0; i < scores.length; i++) {
            result[i] = order.get(i);
        }
        return result;
    }

    /** Proportion des {@code k} premiers du classement de référence retrouvés dans le candidat. */
    static double topKOverlap(int[] reference, int[] candidate, int k) {
        int limit = Math.min(k, Math.min(reference.length, candidate.length));
        if (limit == 0) {
            return 1.0;
        }
        Set<Integer> top = new HashSet<>();
        for (int i = 0; i < limit; i++) {
            top.add(reference[i]);
        }
        int found = 0;
        for (int i = 0; i < limit; i++) {
            if (top.contains(candidate[i])) {
                found++;
            }
        }
        return found / (double) limit;
    }

    /** 1.0 si les deux modèles désignent le même meilleur document, 0.0 sinon. */
    static double top1Agreement(int[] reference, int[] candidate) {
        if (reference.length == 0 || candidate.length == 0) {
            return 0.0;
        }
        return reference[0] == candidate[0] ? 1.0 : 0.0;
    }

    /**
     * Corrélation de rangs de Spearman entre deux jeux de scores.
     *
     * <p>Porte sur les rangs et non sur les scores bruts : deux modèles différents n'ont aucune
     * raison de produire des scores sur la même échelle, seul leur ordre est comparable.</p>
     *
     * <p>Délègue à Commons Math plutôt qu'à la formule abrégée {@code 1 - 6*Σd²/(n(n²-1))} : cette
     * dernière n'est exacte qu'en <b>l'absence totale d'ex æquo</b>. Or des documents quasi
     * identiques — cas courant dans un corpus d'offres — produisent des scores égaux. Commons Math
     * calcule une corrélation de Pearson sur les rangs, qui reste correcte avec des égalités.</p>
     *
     * <p>{@link NaturalRanking} est passé explicitement pour figer le traitement des cas limites :
     * moyenne des rangs ex æquo, et échec bruyant sur un NaN plutôt qu'une valeur silencieusement
     * fausse — un NaN signalerait une inférence cassée, pas un classement à interpréter.</p>
     */
    static double spearman(float[] a, float[] b) {
        if (a.length < 2) {
            return 1.0;
        }
        double[] x = toDoubles(a);
        double[] y = toDoubles(b);

        // Deux modèles peuvent produire un score constant sur tout le lot (modèle dégénéré) :
        // la corrélation n'est alors pas définie. On le signale comme absence d'accord mesurable.
        if (isConstant(x) || isConstant(y)) {
            return Double.NaN;
        }

        SpearmansCorrelation correlation = new SpearmansCorrelation(new NaturalRanking(NaNStrategy.FAILED, TiesStrategy.AVERAGE));
        return correlation.correlation(x, y);
    }

    private static double[] toDoubles(float[] values) {
        double[] result = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = values[i];
        }
        return result;
    }

    private static boolean isConstant(double[] values) {
        for (double value : values) {
            if (value != values[0]) {
                return false;
            }
        }
        return true;
    }
}
