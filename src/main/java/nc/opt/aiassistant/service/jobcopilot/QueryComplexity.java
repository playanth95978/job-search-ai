package nc.opt.aiassistant.service.jobcopilot;

import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Classe une requête utilisateur en « simple » ou « complexe », pour décider si la recherche mérite
 * un passage par le reranker ONNX.
 *
 * <p>Le reranker est de loin l'étape la plus coûteuse de {@code searchHybrid} : il fait passer chaque
 * candidat dans un cross-encoder, là où le reste n'est que deux requêtes SQL et une fusion. Sur
 * « java », il ne rapporte rien — l'intention tient dans un mot, et BM25 comme le vecteur la
 * capturent déjà. Sur « dev java spring boot senior en télétravail sans astreinte », il départage
 * des offres que la fusion classe presque à égalité, parce qu'il lit la requête et le document
 * ensemble plutôt que séparément.
 *
 * <p>Pure et sans dépendance : c'est ce qui la rend exhaustivement testable, et la raison pour
 * laquelle la décision vit ici plutôt que dans le contrôleur ou le service de recherche.
 */
public final class QueryComplexity {

    /**
     * Nombre de termes significatifs à partir duquel une requête est jugée complexe.
     *
     * <p>Cinq : en dessous, l'utilisateur nomme un métier et une techno, ce que la fusion traite
     * bien. Au-delà, il empile des contraintes (séniorité, contrat, lieu, exclusions) dont l'ordre
     * relatif est précisément ce que le cross-encoder sait arbitrer.
     */
    static final int COMPLEX_TERM_COUNT = 5;

    /** Sépare sur tout ce qui n'est ni lettre (accents compris) ni chiffre. */
    private static final Pattern SEPARATOR = Pattern.compile("[^\\p{L}\\p{N}]+");

    /**
     * Mots vides ignorés dans le décompte. Volontairement court : il ne s'agit pas de faire de
     * l'analyse linguistique, seulement d'éviter que « un poste de développeur » compte pour quatre.
     */
    private static final Set<String> STOP_WORDS = Set.of(
        "un",
        "une",
        "des",
        "de",
        "du",
        "le",
        "la",
        "les",
        "l",
        "d",
        "en",
        "au",
        "aux",
        "pour",
        "par",
        "sur",
        "dans",
        "chez",
        "a",
        "the",
        "of",
        "for",
        "in",
        "at",
        "on",
        "to",
        "an",
        "and"
    );

    /**
     * Marqueurs qui rendent une requête complexe quel que soit sa longueur.
     *
     * <p>Une conjonction, une exclusion ou une comparaison exprime une relation <em>entre</em>
     * termes. Un sac de mots ne la représente pas : « java sans astreinte » et « java astreinte »
     * ont la même signature lexicale et le sens inverse. C'est exactement ce que le cross-encoder
     * voit et que la fusion ignore.
     */
    private static final Set<String> RELATION_MARKERS = Set.of(
        "sans",
        "avec",
        "mais",
        "plutot",
        "plutôt",
        "sauf",
        "hors",
        "ni",
        "ou",
        "et",
        "moins",
        "plus",
        "meilleur",
        "meilleure",
        "without",
        "with",
        "but",
        "except",
        "rather",
        "not",
        "nor",
        "or",
        "better",
        "more",
        "less"
    );

    private QueryComplexity() {}

    /** Ce que la requête demande au moteur. */
    public enum Level {
        /** Intention tenant en quelques mots : la fusion vecteur + BM25 suffit. */
        SIMPLE,
        /** Contraintes multiples ou relations entre termes : le reranker gagne son coût. */
        COMPLEX,
    }

    /**
     * Classe {@code query}. Une requête vide est simple : il n'y a alors pas de mots utilisateur à
     * arbitrer, la recherche part du profil.
     */
    public static Level of(String query) {
        if (query == null || query.isBlank()) {
            return Level.SIMPLE;
        }
        // Une question posée en langue naturelle porte une intention que les mots seuls ne rendent pas.
        if (query.indexOf('?') >= 0) {
            return Level.COMPLEX;
        }

        String[] tokens = SEPARATOR.split(query.toLowerCase().trim());
        long meaningful = Arrays.stream(tokens)
            .filter(t -> !t.isEmpty())
            .filter(t -> !STOP_WORDS.contains(t))
            .count();

        boolean hasRelation = Arrays.stream(tokens).anyMatch(RELATION_MARKERS::contains);

        return (hasRelation || meaningful >= COMPLEX_TERM_COUNT) ? Level.COMPLEX : Level.SIMPLE;
    }

    /** Raccourci lisible côté appelant : faut-il payer le reranker pour cette requête ? */
    public static boolean needsReranking(String query) {
        return of(query) == Level.COMPLEX;
    }
}
