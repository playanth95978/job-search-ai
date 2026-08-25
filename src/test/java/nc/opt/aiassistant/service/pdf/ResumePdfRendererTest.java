package nc.opt.aiassistant.service.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * Renders each CV template for real and reads the resulting PDF back.
 *
 * <p>Asserting on extracted text — rather than on the generated XHTML — is deliberate: it is the only
 * check that proves the export is selectable, machine-readable text, which is exactly what the previous
 * html2canvas/jsPDF export (a bitmap) failed to be and why an ATS could not parse it.</p>
 */
class ResumePdfRendererTest {

    private static AnnotationConfigApplicationContext context;
    private static ResumePdfRenderer renderer;

    @BeforeAll
    static void setUp() {
        context = new AnnotationConfigApplicationContext(CvPdfTemplateConfig.class);
        renderer = new ResumePdfRenderer(context.getBean("cvPdfTemplateEngine", org.thymeleaf.spring6.SpringTemplateEngine.class));
    }

    @AfterAll
    static void tearDown() {
        context.close();
    }

    @ParameterizedTest
    @ValueSource(strings = { "modern", "professional", "minimalist", "australian" })
    void rendersEverySectionAsExtractableText(String template) throws IOException {
        byte[] pdf = renderer.render(resume(template, 2));

        assertThat(pdf).startsWith('%', 'P', 'D', 'F');
        String text = textOf(pdf);
        assertThat(text)
            .contains("Jane Doe")
            .contains("jane@example.com")
            .contains("Staff Engineer")
            .contains("Acme Corp")
            .contains("Cut p99 latency by 40%")
            .contains("Kubernetes")
            .contains("MSc Computer Science")
            .contains("Vector Search Engine")
            .contains("AWS Solutions Architect")
            .contains("English");
    }

    @Test
    void keepsRefereesOnTheAustralianTemplateOnly() throws IOException {
        // Section headings are uppercased by `text-transform`, which the renderer bakes into the glyphs
        // it writes — so the extracted text reads REFEREES, not Referees.
        assertThat(textOf(renderer.render(resume("australian", 1))))
            .containsIgnoringCase("Referees")
            .contains("Erin Blake");
        assertThat(textOf(renderer.render(resume("modern", 1)))).doesNotContain("Erin Blake");
    }

    @Test
    void fallsBackToTheDefaultTemplateWhenTheStoredOneIsUnknown() throws IOException {
        assertThat(textOf(renderer.render(resume("some-template-we-removed", 1)))).contains("Jane Doe");
    }

    @Test
    void honoursSectionOrderAndHiddenSections() {
        ResumeDocument base = resume("professional", 1);
        ResumeDocument reordered = withSections(base, List.of("skills", "summary", "experiences"), List.of("projects"));

        String html = renderer.renderHtml(reordered, false);

        assertThat(html.indexOf("Compétences")).isLessThan(html.indexOf("Résumé professionnel"));
        assertThat(html).doesNotContain("Vector Search Engine");
    }

    @Test
    void numbersPagesOnlyWhenTheCvSpillsOverOnePage() throws IOException {
        try (PDDocument onePage = Loader.loadPDF(renderer.render(resume("professional", 1)))) {
            assertThat(onePage.getNumberOfPages()).isEqualTo(1);
            assertThat(new PDFTextStripper().getText(onePage)).doesNotContain("1 / 1");
        }

        byte[] longPdf = renderer.render(resume("professional", 14));
        try (PDDocument multiPage = Loader.loadPDF(longPdf)) {
            assertThat(multiPage.getNumberOfPages()).isGreaterThan(1);
            assertThat(new PDFTextStripper().getText(multiPage)).contains("1 / " + multiPage.getNumberOfPages());
        }
    }

    @Test
    void doesNotSliceAJobEntryAcrossAPageBreak() throws IOException {
        // With `page-break-inside: avoid` on .entry, an entry's heading and its bullets always land on
        // the same page — so no page may start with a bullet whose job title was left on the previous one.
        byte[] pdf = renderer.render(resume("professional", 14));
        try (PDDocument document = Loader.loadPDF(pdf)) {
            for (int page = 2; page <= document.getNumberOfPages(); page++) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String firstLine = stripper
                    .getText(document)
                    .lines()
                    .filter(l -> !l.isBlank())
                    .findFirst()
                    .orElse("");
                assertThat(firstLine).doesNotContain("Cut p99 latency by 40%");
            }
        }
    }

    @Test
    void neverLetsAThemeValueLeakIntoTheStylesheet() {
        ResumeDocument tampered = withTheme(
            resume("modern", 1),
            new ResumeDocument.Theme("red; } body { display: none; ", "#4338ca", "Inter'; x: url(http://evil.test); '", 14)
        );

        String html = renderer.renderHtml(tampered, false);

        assertThat(html).doesNotContain("display: none").doesNotContain("evil.test");
        // Rejected values fall back to the defaults rather than being dropped, so the CV still looks styled.
        assertThat(html).contains("#4f46e5");
    }

    // ── fixtures ──────────────────────────────────────────────────────────────────────────────────

    private static String textOf(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    /** A fully-populated resume; {@code jobs} controls how long it is, to exercise pagination. */
    private static ResumeDocument resume(String template, int jobs) {
        List<ResumeDocument.Experience> experiences = IntStream.range(0, jobs)
            .mapToObj(i ->
                new ResumeDocument.Experience(
                    i == 0 ? "Staff Engineer" : "Senior Engineer " + i,
                    "Acme Corp",
                    // `location` ajoute au record apres l'ecriture de ce test : renseigne pour que
                    // le rendu exerce bien la ligne « entreprise + lieu ».
                    "Noumea",
                    "Jan 20" + (10 + i),
                    "Dec 20" + (11 + i),
                    i == 0,
                    "Owned the search platform end to end, from ingestion to ranking, for a team of eight.",
                    List.of("Cut p99 latency by 40%", "Migrated the cluster to Kubernetes with zero downtime")
                )
            )
            .toList();

        return new ResumeDocument(
            "Mon CV",
            new ResumeDocument.Personal(
                "Jane",
                "Doe",
                "Staff Engineer",
                "jane@example.com",
                "+687 12 34 56",
                "Nouméa",
                "linkedin.com/in/janedoe",
                "github.com/janedoe",
                "jane.example",
                "Full working rights"
            ),
            "Backend engineer with ten years on distributed search systems.",
            experiences,
            List.of(new ResumeDocument.Education("MSc Computer Science", "Université de Lorraine", "2008", "2010", "Distributed systems")),
            List.of(
                new ResumeDocument.Project(
                    "Vector Search Engine",
                    "Hybrid BM25 + embeddings retrieval service.",
                    "github.com/janedoe/vse",
                    List.of("Java", "pgvector")
                )
            ),
            List.of(new ResumeDocument.Certification("AWS Solutions Architect", "Amazon", "2023")),
            List.of(new ResumeDocument.Skill("Java", 5), new ResumeDocument.Skill("Kubernetes", 4)),
            List.of(new ResumeDocument.Language("French", "Native"), new ResumeDocument.Language("English", "C1")),
            List.of(new ResumeDocument.Referee("Erin Blake", "Head of Engineering", "Acme Corp", "erin@example.com", "+687 98 76 54")),
            template,
            new ResumeDocument.Theme("#4f46e5", "#4338ca", "'Inter', sans-serif", 14),
            ResumeDocument.ALL_SECTIONS,
            List.of()
        );
    }

    private static ResumeDocument withSections(ResumeDocument r, List<String> order, List<String> hidden) {
        return new ResumeDocument(
            r.title(),
            r.personal(),
            r.summary(),
            r.experiences(),
            r.education(),
            r.projects(),
            r.certifications(),
            r.skills(),
            r.languages(),
            r.referees(),
            r.template(),
            r.theme(),
            order,
            hidden
        );
    }

    private static ResumeDocument withTheme(ResumeDocument r, ResumeDocument.Theme theme) {
        return new ResumeDocument(
            r.title(),
            r.personal(),
            r.summary(),
            r.experiences(),
            r.education(),
            r.projects(),
            r.certifications(),
            r.skills(),
            r.languages(),
            r.referees(),
            r.template(),
            theme,
            r.sectionOrder(),
            r.hiddenSections()
        );
    }
}
