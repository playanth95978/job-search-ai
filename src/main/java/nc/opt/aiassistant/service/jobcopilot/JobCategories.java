package nc.opt.aiassistant.service.jobcopilot;

import java.util.List;

/**
 * The common job-family vocabulary stored in {@code job_offer.category}.
 *
 * <p>Sources are mapped onto it by {@link SourceCategoryMapper} (ROME codes, Adzuna tags) and, for the
 * boards that publish no taxonomy at all, inferred from the title by {@link JobCategoryClassifier}.
 * Having one vocabulary is what lets the filter panel show a coherent, translatable list instead of a
 * mix of ROME domaines and English board labels.
 *
 * <p>The filter options themselves are NOT read from this list: they come from a {@code GROUP BY} over
 * the column, so the panel only ever offers values that actually match something. This list is the
 * closed set of values the writers may produce, and the keys the UI has translations for.
 */
public final class JobCategories {

    public static final String TECH = "tech";
    public static final String DATA = "data";
    public static final String DESIGN = "design";
    public static final String PRODUCT = "product";
    public static final String SALES = "sales";
    public static final String MARKETING = "marketing";
    public static final String FINANCE = "finance";
    public static final String HR = "hr";
    public static final String HEALTH = "health";
    public static final String EDUCATION = "education";
    public static final String CONSTRUCTION = "construction";
    public static final String LOGISTICS = "logistics";
    public static final String HOSPITALITY = "hospitality";
    public static final String ADMIN = "admin";
    public static final String INDUSTRY = "industry";
    public static final String MAINTENANCE = "maintenance";
    public static final String AGRICULTURE = "agriculture";
    public static final String ARTS = "arts";
    public static final String SERVICES = "services";
    public static final String OTHER = "other";

    /** Every family, for the tests that assert the mappers stay inside the vocabulary. */
    public static final List<String> ALL = List.of(
        TECH,
        DATA,
        DESIGN,
        PRODUCT,
        SALES,
        MARKETING,
        FINANCE,
        HR,
        HEALTH,
        EDUCATION,
        CONSTRUCTION,
        LOGISTICS,
        HOSPITALITY,
        ADMIN,
        INDUSTRY,
        MAINTENANCE,
        AGRICULTURE,
        ARTS,
        SERVICES,
        OTHER
    );

    private JobCategories() {}
}
