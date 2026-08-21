package nc.opt.aiassistant.service.pdf;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Server-side mirror of the CV Builder's front-end {@code Resume} model (resume.model.ts), used as the
 * rendering model for the PDF templates.
 *
 * <p>The resume is persisted as an opaque JSON blob ({@code resume.data}) so the front-end schema can
 * evolve without a migration; this record is a <em>tolerant</em> read view of it — unknown properties are
 * ignored and every collection is normalized to a non-null list, so a template never has to null-check.</p>
 *
 * <p>Client-only concerns (element ids, {@code updatedAt}) are deliberately absent.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ResumeDocument(
    String title,
    Personal personal,
    String summary,
    List<Experience> experiences,
    List<Education> education,
    List<Project> projects,
    List<Certification> certifications,
    List<Skill> skills,
    List<Language> languages,
    List<Referee> referees,
    String template,
    Theme theme,
    List<String> sectionOrder,
    List<String> hiddenSections
) {
    /** Section keys, in the default display order used when the resume carries no explicit order. */
    public static final List<String> ALL_SECTIONS = List.of(
        "summary",
        "experiences",
        "education",
        "skills",
        "languages",
        "projects",
        "certifications"
    );

    public ResumeDocument {
        title = blankToNull(title);
        personal = personal == null ? Personal.EMPTY : personal;
        summary = blankToNull(summary);
        experiences = nonNull(experiences);
        education = nonNull(education);
        projects = nonNull(projects);
        certifications = nonNull(certifications);
        skills = nonNull(skills);
        languages = nonNull(languages);
        referees = nonNull(referees);
        template = (template == null || template.isBlank()) ? "modern" : template;
        theme = theme == null ? Theme.DEFAULT : theme;
        sectionOrder = nonNull(sectionOrder).isEmpty() ? ALL_SECTIONS : nonNull(sectionOrder);
        hiddenSections = nonNull(hiddenSections);
    }

    /**
     * Sections to render, in order: the resume's own order minus the hidden ones, with any section the
     * stored order predates appended so a newer front-end schema never silently drops content.
     */
    public List<String> visibleSections() {
        Set<String> ordered = new LinkedHashSet<>(sectionOrder);
        ordered.addAll(ALL_SECTIONS);
        List<String> visible = new ArrayList<>(ordered);
        visible.removeAll(hiddenSections);
        return visible;
    }

    /** {@code "Jane Doe"}, or an empty string when the identity is not filled in yet. */
    public String fullName() {
        return join(" ", personal.firstName(), personal.lastName());
    }

    /**
     * Single-line contact string used by the header of every template.
     *
     * <p>Separated by a pipe rather than a middle dot: resume parsers split contact lines on a small set
     * of conventional delimiters, and "|" is in every one of them. The DOCX export already used it.
     */
    public String contactLine() {
        return join(
            "  |  ",
            personal.email(),
            personal.phone(),
            personal.location(),
            personal.linkedin(),
            personal.github(),
            personal.website()
        );
    }

    /** Comma-free, dot-separated skill list, for the templates that render skills as one line. */
    public String skillNames() {
        return skills
            .stream()
            .map(Skill::name)
            .filter(ResumeDocument::isFilled)
            .reduce((a, b) -> a + " · " + b)
            .orElse("");
    }

    /** {@code "French (Native) · English (C1)"}, for the templates that render languages as one line. */
    public String languagesLine() {
        return languages
            .stream()
            .map(Language::label)
            .filter(ResumeDocument::isFilled)
            .reduce((a, b) -> a + " · " + b)
            .orElse("");
    }

    /** File-name-safe slug derived from the candidate's name, falling back to the resume title. */
    public String slug() {
        String base = fullName();
        if (base.isEmpty()) {
            base = title == null ? "cv" : title;
        }
        String slug = base.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug.isEmpty() ? "cv" : slug;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Personal(
        String firstName,
        String lastName,
        String title,
        String email,
        String phone,
        String location,
        String linkedin,
        String github,
        String website,
        String workingRights
    ) {
        static final Personal EMPTY = new Personal(null, null, null, null, null, null, null, null, null, null);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Experience(
        String jobTitle,
        String company,
        /** Where the role was based; absent on resumes saved before the field existed. */
        String location,
        String startDate,
        String endDate,
        boolean current,
        String description,
        List<String> achievements
    ) {
        public Experience {
            achievements = nonNull(achievements);
        }

        /**
         * {@code "Jan 2021 — Present"}. Rendering the range in Java rather than concatenating in the
         * template keeps a missing date from printing as the literal {@code null}, and lets each
         * template pass its own wording for an ongoing role.
         */
        public String dateRange(String presentLabel) {
            return join(" — ", startDate, current ? presentLabel : endDate);
        }

        /** {@code "Acme, Sydney NSW"} — one line whether or not either half is filled in. */
        public String companyLine() {
            return join(", ", company, location);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Education(String degree, String school, String startDate, String endDate, String description) {
        public String dateRange() {
            return join(" — ", startDate, endDate);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Project(String name, String description, String url, List<String> technologies) {
        public Project {
            technologies = nonNull(technologies);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Certification(String name, String issuer, String date) {
        public String issuedLine() {
            return join(" · ", issuer, date);
        }
    }

    /** {@code level} on a 1..5 scale (0 = unspecified); {@link #barWidth()} maps it to a percentage. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Skill(String name, int level) {
        public int barWidth() {
            return Math.clamp(level, 0, 5) * 20;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Language(String name, String level) {
        /** {@code "French (Native)"} — drops the parentheses entirely when no level was given. */
        public String label() {
            return isFilled(level) ? join(" ", name) + " (" + level.trim() + ")" : join("", name);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Referee(String name, String position, String company, String email, String phone) {
        /** {@code "Head of Engineering, Acme"} — omits the separator when either half is missing. */
        public String role() {
            return join(", ", position, company);
        }

        public String contact() {
            return join("  ·  ", email, phone);
        }
    }

    /** Colors/typography chosen in the builder. Values are sanitized before reaching the CSS. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Theme(String primaryColor, String secondaryColor, String fontFamily, int fontSize) {
        static final Theme DEFAULT = new Theme("#4f46e5", "#4338ca", "'Inter', sans-serif", 14);

        public Theme {
            primaryColor = primaryColor == null ? DEFAULT.primaryColor() : primaryColor;
            secondaryColor = secondaryColor == null ? DEFAULT.secondaryColor() : secondaryColor;
            fontFamily = fontFamily == null ? DEFAULT.fontFamily() : fontFamily;
            fontSize = fontSize <= 0 ? DEFAULT.fontSize() : fontSize;
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────────────────────────

    static boolean isFilled(String s) {
        return s != null && !s.isBlank();
    }

    private static <T> List<T> nonNull(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static String blankToNull(String s) {
        return isFilled(s) ? s : null;
    }

    private static String join(String separator, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (isFilled(part)) {
                if (!sb.isEmpty()) {
                    sb.append(separator);
                }
                sb.append(part.trim());
            }
        }
        return sb.toString();
    }
}
