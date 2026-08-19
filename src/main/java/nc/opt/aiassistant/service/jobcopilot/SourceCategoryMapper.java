package nc.opt.aiassistant.service.jobcopilot;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import nc.opt.aiassistant.domain.enumeration.JobSource;
import org.springframework.stereotype.Component;

/**
 * Translates a source's own taxonomy into the common job-family vocabulary.
 *
 * <p>Preferred over guessing from the title: France Travail states a ROME code per offer and Adzuna a
 * category tag, both curated by the source. What they do not share is a vocabulary, hence this mapping —
 * it is a correspondence table, not a classifier, and it only grows when a new source is plugged in.
 *
 * <p>ROME is keyed on the <em>domaine professionnel</em> (letter + two digits, e.g. {@code M18} =
 * "Systèmes d'information et de télécommunication") rather than the grand domaine (the letter alone):
 * {@code M} covers all of "Support à l'entreprise", so a developer would land in admin instead of tech.
 * Unknown domaines fall back to the grand domaine, which is coarse but never wrong.
 */
@Component
public class SourceCategoryMapper {

    /** ROME domaine professionnel (3 chars) → family. */
    private static final Map<String, String> ROME_DOMAINES = Map.ofEntries(
        Map.entry("M18", JobCategories.TECH),
        Map.entry("M14", JobCategories.ADMIN),
        Map.entry("M13", JobCategories.ADMIN),
        Map.entry("M15", JobCategories.HR),
        Map.entry("M16", JobCategories.ADMIN),
        Map.entry("M17", JobCategories.MARKETING),
        Map.entry("M12", JobCategories.FINANCE),
        Map.entry("M11", JobCategories.DATA),
        Map.entry("C11", JobCategories.FINANCE),
        Map.entry("C12", JobCategories.FINANCE),
        Map.entry("C13", JobCategories.FINANCE),
        Map.entry("C14", JobCategories.FINANCE),
        Map.entry("C15", JobCategories.FINANCE),
        Map.entry("D14", JobCategories.SALES),
        Map.entry("D15", JobCategories.SALES),
        Map.entry("E11", JobCategories.MARKETING),
        Map.entry("E12", JobCategories.MARKETING),
        Map.entry("E13", JobCategories.DESIGN),
        Map.entry("E14", JobCategories.MARKETING),
        Map.entry("K21", JobCategories.EDUCATION),
        Map.entry("J11", JobCategories.HEALTH),
        Map.entry("J12", JobCategories.HEALTH),
        Map.entry("J13", JobCategories.HEALTH),
        Map.entry("J14", JobCategories.HEALTH),
        Map.entry("J15", JobCategories.HEALTH)
    );

    /** ROME grand domaine (1 char) → family, the fallback when the domaine is not mapped above. */
    private static final Map<String, String> ROME_GRANDS_DOMAINES = Map.ofEntries(
        Map.entry("A", JobCategories.AGRICULTURE),
        Map.entry("B", JobCategories.ARTS),
        Map.entry("C", JobCategories.FINANCE),
        Map.entry("D", JobCategories.SALES),
        Map.entry("E", JobCategories.MARKETING),
        Map.entry("F", JobCategories.CONSTRUCTION),
        Map.entry("G", JobCategories.HOSPITALITY),
        Map.entry("H", JobCategories.INDUSTRY),
        Map.entry("I", JobCategories.MAINTENANCE),
        Map.entry("J", JobCategories.HEALTH),
        Map.entry("K", JobCategories.SERVICES),
        Map.entry("L", JobCategories.ARTS),
        Map.entry("M", JobCategories.ADMIN),
        Map.entry("N", JobCategories.LOGISTICS)
    );

    /** Adzuna category tag → family. */
    private static final Map<String, String> ADZUNA_TAGS = Map.ofEntries(
        Map.entry("it-jobs", JobCategories.TECH),
        Map.entry("engineering-jobs", JobCategories.INDUSTRY),
        Map.entry("scientific-qa-jobs", JobCategories.DATA),
        Map.entry("creative-design-jobs", JobCategories.DESIGN),
        Map.entry("sales-jobs", JobCategories.SALES),
        Map.entry("retail-jobs", JobCategories.SALES),
        Map.entry("pr-advertising-marketing-jobs", JobCategories.MARKETING),
        Map.entry("accounting-finance-jobs", JobCategories.FINANCE),
        Map.entry("hr-jobs", JobCategories.HR),
        Map.entry("healthcare-nursing-jobs", JobCategories.HEALTH),
        Map.entry("teaching-jobs", JobCategories.EDUCATION),
        Map.entry("trade-construction-jobs", JobCategories.CONSTRUCTION),
        Map.entry("property-jobs", JobCategories.CONSTRUCTION),
        Map.entry("logistics-warehouse-jobs", JobCategories.LOGISTICS),
        Map.entry("travel-jobs", JobCategories.HOSPITALITY),
        Map.entry("hospitality-catering-jobs", JobCategories.HOSPITALITY),
        Map.entry("admin-jobs", JobCategories.ADMIN),
        Map.entry("customer-services-jobs", JobCategories.SERVICES),
        Map.entry("social-work-jobs", JobCategories.SERVICES),
        Map.entry("manufacturing-jobs", JobCategories.INDUSTRY),
        Map.entry("maintenance-jobs", JobCategories.MAINTENANCE),
        Map.entry("energy-oil-gas-jobs", JobCategories.INDUSTRY),
        Map.entry("legal-jobs", JobCategories.SERVICES),
        Map.entry("consultancy-jobs", JobCategories.SERVICES),
        Map.entry("charity-voluntary-jobs", JobCategories.SERVICES),
        Map.entry("domestic-help-cleaning-jobs", JobCategories.SERVICES),
        Map.entry("part-time-jobs", JobCategories.OTHER),
        Map.entry("graduate-jobs", JobCategories.OTHER),
        Map.entry("other-general-jobs", JobCategories.OTHER),
        Map.entry("unknown", JobCategories.OTHER)
    );

    /**
     * The family a source label maps to, or empty when the source has no usable taxonomy for it — in
     * which case the caller falls back to classifying the title.
     *
     * @param source   which board the label comes from, since the vocabularies are source-specific.
     * @param rawLabel the source's own value (a ROME code, an Adzuna tag).
     */
    public Optional<String> map(JobSource source, String rawLabel) {
        if (source == null || rawLabel == null || rawLabel.isBlank()) {
            return Optional.empty();
        }
        String normalized = rawLabel.trim().toUpperCase(Locale.ROOT);
        return switch (source) {
            case FRANCE_TRAVAIL -> mapRome(normalized);
            case ADZUNA -> Optional.ofNullable(ADZUNA_TAGS.get(rawLabel.trim().toLowerCase(Locale.ROOT)));
            default -> Optional.empty();
        };
    }

    private Optional<String> mapRome(String romeCode) {
        if (romeCode.length() < 3) {
            return romeCode.isEmpty() ? Optional.empty() : Optional.ofNullable(ROME_GRANDS_DOMAINES.get(romeCode.substring(0, 1)));
        }
        String domaine = ROME_DOMAINES.get(romeCode.substring(0, 3));
        return domaine != null ? Optional.of(domaine) : Optional.ofNullable(ROME_GRANDS_DOMAINES.get(romeCode.substring(0, 1)));
    }
}
