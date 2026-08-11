package nc.opt.aiassistant.service.reranker;

import java.util.ArrayList;
import java.util.List;

/**
 * Corpus partagé par les bancs de reranking : requêtes et offres d'emploi de pertinence contrastée.
 *
 * <p><b>Pourquoi un vrai corpus</b> — la fidélité de classement ne veut rien dire sur des tokens
 * aléatoires : deux modèles y produiraient des ordres arbitraires, et leur écart mesurerait le bruit
 * plutôt que la qualité. Il faut donc des documents dont certains répondent nettement à la requête
 * et d'autres pas du tout, pour qu'un bon classement se distingue d'un mauvais.</p>
 *
 * <p>Les documents mêlent trois niveaux : très pertinent (le métier exact), voisin (même famille
 * technique) et hors sujet (autre secteur). Un reranker utile doit remonter les premiers et
 * repousser les derniers.</p>
 */
final class RerankerTestCorpus {

    private RerankerTestCorpus() {}

    /** Requêtes types, telles qu'un candidat les formulerait. */
    static final List<String> QUERIES = List.of(
        "développeur java spring boot microservices",
        "data engineer python spark pipelines données",
        "infirmier diplômé d'état service de nuit",
        "comptable expérimenté liasse fiscale",
        "administrateur systèmes linux kubernetes"
    );

    /** Métiers très pertinents pour au moins une requête. */
    private static final String[][] RELEVANT = {
        {
            "Développeur Java Senior",
            "Conception de microservices Spring Boot, API REST, PostgreSQL, Docker, CI/CD, revues de code et tests automatisés.",
        },
        {
            "Ingénieur Backend Java",
            "Développement d'applications Spring Boot en architecture microservices, messagerie Kafka, observabilité, Kubernetes.",
        },
        {
            "Data Engineer",
            "Construction de pipelines de données en Python et Spark, orchestration Airflow, modélisation entrepôt, qualité des données.",
        },
        {
            "Ingénieur Données Big Data",
            "Traitement distribué Spark, ingestion temps réel, Python, SQL avancé, optimisation de jobs et coûts cloud.",
        },
        {
            "Infirmier Diplômé d'État",
            "Soins auprès des patients en service hospitalier, travail de nuit en roulement, surveillance clinique, dossier de soins.",
        },
        {
            "Infirmier de nuit en EHPAD",
            "Prise en charge des résidents la nuit, distribution des traitements, coordination avec l'équipe médicale.",
        },
        {
            "Comptable Général",
            "Tenue de la comptabilité, révision des comptes, établissement de la liasse fiscale, déclarations de TVA, bilan annuel.",
        },
        {
            "Collaborateur Comptable Confirmé",
            "Gestion d'un portefeuille clients, bilans, liasses fiscales, conseil et relation client en cabinet d'expertise.",
        },
        {
            "Administrateur Systèmes Linux",
            "Exploitation de serveurs Linux, orchestration Kubernetes, automatisation Ansible, supervision et sécurité.",
        },
        {
            "Ingénieur DevOps",
            "Industrialisation des déploiements Kubernetes, Terraform, GitLab CI, observabilité Prometheus sur infrastructure Linux.",
        },
    };

    /** Métiers voisins : même famille, mais pas la réponse attendue. */
    private static final String[][] NEIGHBOUR = {
        {
            "Développeur Frontend Angular",
            "Interfaces web en Angular et TypeScript, intégration d'API REST, tests end-to-end, accessibilité.",
        },
        { "Développeur PHP Symfony", "Maintenance et évolution d'applications Symfony, MySQL, intégration continue." },
        {
            "Analyste Business Intelligence",
            "Conception de tableaux de bord Power BI, requêtes SQL, restitution auprès des directions métier.",
        },
        { "Aide-soignant", "Accompagnement des patients dans les gestes du quotidien, en lien avec l'équipe soignante." },
        { "Assistant Comptable", "Saisie des pièces comptables, rapprochements bancaires, classement et archivage." },
        {
            "Technicien Support Informatique",
            "Assistance aux utilisateurs, gestion du parc, incidents de niveau 1 et 2, Windows et réseau.",
        },
    };

    /** Hors sujet complet : aucun recouvrement de vocabulaire métier. */
    private static final String[][] IRRELEVANT = {
        {
            "Chef de Rang",
            "Service en salle dans un restaurant gastronomique, accueil des clients, dressage et conseil sur la carte des vins.",
        },
        {
            "Conducteur de Travaux",
            "Pilotage de chantiers de construction, suivi des sous-traitants, sécurité, respect des délais et du budget.",
        },
        { "Coiffeur Visagiste", "Coupes, colorations et conseils personnalisés en salon, fidélisation de la clientèle." },
        {
            "Chauffeur Poids Lourd",
            "Livraison régionale, chargement et déchargement, respect des temps de conduite et de la réglementation.",
        },
    };

    /**
     * Construit {@code count} documents en alternant les trois niveaux de pertinence, de façon
     * déterministe : deux modèles voient exactement le même corpus, dans le même ordre.
     */
    static List<String> documents(int count) {
        List<String[]> pool = new ArrayList<>();
        // L'alternance évite que les pertinents soient tous en tête : un classement d'entrée déjà
        // trié fausserait la mesure en rendant tout reranker apparemment bon.
        int max = Math.max(RELEVANT.length, Math.max(NEIGHBOUR.length, IRRELEVANT.length));
        for (int i = 0; i < max; i++) {
            if (i < RELEVANT.length) pool.add(RELEVANT[i]);
            if (i < NEIGHBOUR.length) pool.add(NEIGHBOUR[i]);
            if (i < IRRELEVANT.length) pool.add(IRRELEVANT[i]);
        }

        List<String> docs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String[] entry = pool.get(i % pool.size());
            // Le suffixe rend chaque document unique sans altérer son sens : au-delà de la taille du
            // pool on répète les métiers, mais jamais deux textes strictement identiques.
            docs.add(format(entry[0], entry[1], i / pool.size()));
        }
        return docs;
    }

    private static String format(String title, String description, int variant) {
        String company = "Société " + (char) ('A' + (variant % 26));
        return (
            "OFFRE D'EMPLOI: " +
            title +
            "\n" +
            "ENTREPRISE: " +
            company +
            "\n" +
            "LOCALISATION: Nouméa\n" +
            "TYPE DE CONTRAT: CDI\n" +
            "DESCRIPTION DU POSTE: " +
            description
        );
    }
}
