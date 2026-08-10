package nc.opt.aiassistant.service.pdf;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

/**
 * Renders a {@link ResumeDocument} to PDF: Thymeleaf produces XHTML + print CSS, openhtmltopdf lays it
 * out onto A4 pages.
 *
 * <p>The output is real, selectable, ATS-parseable text — which is the whole point of doing this here
 * rather than screenshotting the live preview in the browser. The page-breaking rules that keep an entry
 * from being sliced across two pages live in the templates' stylesheet
 * ({@code page-break-inside: avoid}, {@code orphans}/{@code widows}), not in this class.</p>
 */
@Slf4j
@Service
public class ResumePdfRenderer {

    /** Templates that have an XHTML counterpart under {@code resources/templates/cv/}. */
    private static final Set<String> KNOWN_TEMPLATES = Set.of("modern", "professional", "minimalist", "australian");
    private static final String DEFAULT_TEMPLATE = "modern";

    private final SpringTemplateEngine templateEngine;
    private final List<EmbeddedFont> embeddedFonts;

    public ResumePdfRenderer(@Qualifier("cvPdfTemplateEngine") SpringTemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
        this.embeddedFonts = discoverFonts();
    }

    /**
     * Renders the resume with its own template and theme.
     *
     * <p>Page numbering is decided by rendering rather than guessing: the document is laid out once
     * without a footer, and only re-rendered with a {@code "n / total"} footer when it turns out to span
     * more than one page. A single-page CV — the common case — therefore carries no stray "1 / 1".</p>
     */
    public byte[] render(ResumeDocument resume) {
        byte[] withoutFooter = renderPass(resume, false);
        return pageCount(withoutFooter) > 1 ? renderPass(resume, true) : withoutFooter;
    }

    /** The XHTML that would be rendered. Exposed for tests and for debugging a layout without a PDF viewer. */
    public String renderHtml(ResumeDocument resume, boolean pageNumbers) {
        Context context = new Context(Locale.FRANCE);
        context.setVariable("cv", resume);
        context.setVariable("sections", resume.visibleSections());
        context.setVariable("theme", PdfTheme.from(resume.theme()));
        context.setVariable("pageNumbers", pageNumbers);
        return templateEngine.process(templateNameFor(resume.template()), context);
    }

    private byte[] renderPass(ResumeDocument resume, boolean pageNumbers) {
        String xhtml = renderHtml(resume, pageNumbers);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            // No base URI: the templates are fully self-contained (inline CSS, no images, no web fonts),
            // so the renderer must never reach out to the network to resolve a resource.
            builder.withHtmlContent(xhtml, null);
            embeddedFonts.forEach(font -> builder.useFont(font::open, font.family(), font.weight(), font.style(), true));
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to render resume PDF", e);
        }
    }

    private int pageCount(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return document.getNumberOfPages();
        } catch (IOException e) {
            log.warn("Could not read back the rendered PDF to count its pages: {}", e.getMessage());
            return 1;
        }
    }

    private String templateNameFor(String template) {
        return KNOWN_TEMPLATES.contains(template) ? template : DEFAULT_TEMPLATE;
    }

    /**
     * Registers any TrueType font dropped into {@code src/main/resources/fonts/}, so the builder's font
     * choices (Inter, Roboto, …) can be honoured exactly without a code change.
     *
     * <p>Naming convention: {@code Family.ttf}, {@code Family-Bold.ttf}, {@code Family-Italic.ttf},
     * {@code Family-BoldItalic.ttf}. When the directory is empty — which it is by default, to avoid
     * committing font binaries — every template falls back to PDFBox's base-14 fonts. Those cover
     * Latin-1, hence French, Spanish and German; a resume translated into a non-Latin script is the case
     * that actually requires dropping a font in here.</p>
     */
    private List<EmbeddedFont> discoverFonts() {
        List<EmbeddedFont> fonts = new ArrayList<>();
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources("classpath*:/fonts/*.ttf");
            for (Resource resource : resources) {
                String fileName = resource.getFilename();
                if (fileName == null) {
                    continue;
                }
                String base = fileName.substring(0, fileName.length() - ".ttf".length());
                int dash = base.indexOf('-');
                String family = dash < 0 ? base : base.substring(0, dash);
                String variant = dash < 0 ? "" : base.substring(dash + 1).toLowerCase(Locale.ROOT);
                int weight = variant.contains("bold") ? 700 : 400;
                FontStyle style = variant.contains("italic") || variant.contains("oblique") ? FontStyle.ITALIC : FontStyle.NORMAL;
                fonts.add(new EmbeddedFont(resource, family, weight, style));
            }
        } catch (IOException e) {
            log.warn("Could not scan classpath:/fonts for resume PDF fonts: {}", e.getMessage());
        }
        if (!fonts.isEmpty()) {
            log.info("Resume PDF: registered {} embedded font file(s)", fonts.size());
        }
        return List.copyOf(fonts);
    }

    /** A font file to embed. {@link #open()} must hand out a fresh stream on every call. */
    private record EmbeddedFont(Resource resource, String family, int weight, FontStyle style) {
        InputStream open() {
            try {
                return resource.getInputStream();
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot open embedded font " + resource.getFilename(), e);
            }
        }
    }
}
