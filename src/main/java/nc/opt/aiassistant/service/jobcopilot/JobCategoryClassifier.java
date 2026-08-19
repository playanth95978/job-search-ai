package nc.opt.aiassistant.service.jobcopilot;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import nc.opt.aiassistant.domain.JobOffer;
import org.springframework.stereotype.Component;

/**
 * Assigns a job family to an offer so {@code job_offer.category} can be filtered with a plain
 * {@code Specification} like every other criterion.
 *
 * <p>The sources share no taxonomy — Emploi.nc, Adzuna and the ATS boards each categorise their own
 * way and several send nothing at all — so the family is inferred from the title, the one field every
 * source fills. Accents and case are stripped before matching, keyword groups are tried in order, and
 * anything unmatched lands in {@link #OTHER} rather than being left null: a null would be
 * indistinguishable from "not classified yet" in the facet counts.
 *
 * <p>Keywords match at a word boundary and as a prefix, so "develop" catches both "developer" and
 * "développeur" while "ux" no longer fires inside "travaux" — plain substring matching mislabels short
 * keywords, which is exactly how "Conducteur de travaux" once came out as design.
 *
 * <p>This class is the single place the mapping lives: ingestion stamps offers through it, and the
 * backfill job re-runs it over the existing corpus.
 */
@Component
public class JobCategoryClassifier {

    public static final String OTHER = "other";

    private record Family(String id, Pattern pattern) {}

    /** {@code kw1|kw2|…} over the family's keywords, each taken literally. */
    private static Family family(String id, String... keywords) {
        String alternatives = String.join(
            "|",
            List.of(keywords)
                .stream()
                .map(kw -> "\\b" + Pattern.quote(kw))
                .toList()
        );
        return new Family(id, Pattern.compile(alternatives));
    }

    /**
     * Ordered: the first group whose keyword appears in the title wins, so the more specific families
     * come before the broad ones ("data analyst" must not be swallowed by "analyst" under tech).
     */
    private static final List<Family> FAMILIES = List.of(
        family("data", "data", "donnee", "analyst", "analytics", "machine learning", "scientist", "statisticien"),
        family(
            "tech",
            "develop",
            "engineer",
            "ingenieur",
            "software",
            "devops",
            "sysadmin",
            "systeme",
            "reseau",
            "cyber",
            "fullstack",
            "full-stack",
            "backend",
            "frontend",
            "informatique",
            "technicien it",
            "programmeur"
        ),
        family("design", "design", "ux", "graphis", "infographi", "directeur artistique"),
        family("product", "product owner", "product manager", "chef de produit", "scrum", "agile coach"),
        family("sales", "commercial", "sales", "vente", "vendeur", "business developer", "account manager"),
        family("marketing", "marketing", "communication", "community manager", "seo", "growth"),
        family("finance", "comptab", "financ", "audit", "controleur de gestion", "tresorerie", "credit"),
        family("hr", "ressources humaines", "recrut", "talent", "paie", "formation professionnelle"),
        family("health", "infirmi", "medec", "soignant", "sante", "pharmac", "kine", "dentaire", "sage-femme"),
        family("education", "enseign", "formateur", "professeur", "educateur", "instituteur"),
        family("construction", "btp", "chantier", "macon", "electricien", "plombier", "travaux", "soudeur", "peintre"),
        family("logistics", "logistiq", "magasinier", "cariste", "chauffeur", "livreur", "transport", "supply chain"),
        family("hospitality", "cuisin", "serveur", "restaurat", "hotel", "barman", "patissier", "receptionniste"),
        family("admin", "assistant", "secretaire", "administratif", "accueil", "gestionnaire")
    );

    /** The offer's job family, never null. */
    public String classify(JobOffer offer) {
        return classify(offer == null ? null : offer.getTitle());
    }

    /** The job family a title belongs to, never null. */
    public String classify(String title) {
        String normalized = normalize(title);
        if (normalized.isBlank()) {
            return OTHER;
        }
        return FAMILIES.stream()
            .filter(family -> family.pattern().matcher(normalized).find())
            .map(Family::id)
            .findFirst()
            .orElse(OTHER);
    }

    /** Lower-cased and accent-stripped, so "Ingénieur" and "ingenieur" match the same keyword. */
    private String normalize(String title) {
        if (title == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(title, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }
}
