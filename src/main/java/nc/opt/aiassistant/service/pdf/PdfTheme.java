package nc.opt.aiassistant.service.pdf;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The user's theme, sanitized into values that are safe to interpolate straight into a stylesheet.
 *
 * <p>Two problems are solved here. First, injection: {@code theme.primaryColor} and
 * {@code theme.fontFamily} are free-form strings coming from the stored resume JSON, and the templates
 * write them <em>unescaped</em> into a {@code <style>} block (they have to — an XML-escaped
 * {@code &apos;} inside a CSS declaration is not what we want to reason about). Everything that reaches
 * a template through this record has been validated against a strict pattern or replaced by a default.</p>
 *
 * <p>Second, renderer capability: openhtmltopdf implements CSS 2.1, so {@code var()} custom properties —
 * which is how the live Angular preview themes itself — are not available. The colors are therefore
 * baked into the generated CSS at render time instead.</p>
 */
public record PdfTheme(String primary, String secondary, String primarySoft, String fontStack, String baseSize) {
    private static final Pattern HEX_COLOR = Pattern.compile("#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})");
    private static final Pattern FAMILY_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9 ]{0,31}");

    private static final String DEFAULT_PRIMARY = "#4f46e5";
    private static final String DEFAULT_SECONDARY = "#4338ca";

    public static PdfTheme from(ResumeDocument.Theme theme) {
        String primary = color(theme.primaryColor(), DEFAULT_PRIMARY);
        String secondary = color(theme.secondaryColor(), DEFAULT_SECONDARY);
        return new PdfTheme(primary, secondary, softTint(primary), fontStack(theme.fontFamily()), baseSize(theme.fontSize()));
    }

    private static String color(String value, String fallback) {
        String trimmed = value == null ? "" : value.trim();
        return HEX_COLOR.matcher(trimmed).matches() ? trimmed : fallback;
    }

    /**
     * A 12%-opacity wash of the primary color, used for chip/tag backgrounds. Emitted as {@code rgba()}
     * rather than a pre-blended hex so it stays correct over any surface.
     */
    private static String softTint(String hex) {
        String normalized =
            hex.length() == 4 ? "#" + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2) + hex.charAt(3) + hex.charAt(3) : hex;
        int r = Integer.parseInt(normalized.substring(1, 3), 16);
        int g = Integer.parseInt(normalized.substring(3, 5), 16);
        int b = Integer.parseInt(normalized.substring(5, 7), 16);
        return "rgba(" + r + ", " + g + ", " + b + ", 0.12)";
    }

    /**
     * Maps the builder's CSS font stack onto one openhtmltopdf can actually resolve.
     *
     * <p>The chosen family is kept at the head of the stack so that dropping a matching TTF into
     * {@code src/main/resources/fonts/} (see {@link ResumePdfRenderer}) makes it take effect with no code
     * change; the generic keyword that follows always resolves, because PDFBox's base-14 fonts are
     * registered for {@code serif} / {@code sans-serif} / {@code monospace} out of the box.</p>
     */
    private static String fontStack(String cssFontFamily) {
        String first = cssFontFamily == null ? "" : cssFontFamily.split(",")[0].replace("'", "").replace("\"", "").trim();
        String generic = genericFor(cssFontFamily);
        if (!FAMILY_NAME.matcher(first).matches()) {
            return generic;
        }
        return "'" + first + "', " + generic;
    }

    private static String genericFor(String cssFontFamily) {
        String lower = cssFontFamily == null ? "" : cssFontFamily.toLowerCase(Locale.ROOT);
        if (lower.contains("monospace") || lower.contains("courier")) {
            return "monospace";
        }
        if (lower.contains("serif") && !lower.contains("sans-serif")) {
            return "serif";
        }
        if (lower.contains("georgia") || lower.contains("times")) {
            return "serif";
        }
        return "sans-serif";
    }

    /**
     * The builder stores a screen size in CSS pixels; print wants points. At the CSS-standard 96 dpi
     * that is a flat 0.75 ratio (14px → 10.5pt), which keeps the PDF visually identical to the preview.
     */
    private static String baseSize(int pxFontSize) {
        int px = Math.clamp(pxFontSize, 8, 24);
        return String.format(Locale.ROOT, "%.2fpt", px * 0.75);
    }
}
