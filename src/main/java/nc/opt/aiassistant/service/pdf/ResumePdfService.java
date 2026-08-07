package nc.opt.aiassistant.service.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import nc.opt.aiassistant.domain.CandidateProfile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

/**
 * Renders a candidate's résumé (Markdown or structured profile) into a plain, ATS-acceptable PDF.
 * <p>
 * Uses PDFBox (already on the classpath via spring-ai-pdf-document-reader) with the built-in
 * Helvetica font, so text is limited to WinAnsi — content is sanitized accordingly.
 */
@Service
public class ResumePdfService {

    private static final float MARGIN = 50f;
    private static final float FONT_SIZE = 11f;
    private static final float LEADING = 14f;

    /** Builds a PDF from the profile's Markdown résumé, falling back to its structured fields. */
    public byte[] fromProfile(CandidateProfile profile) {
        String text =
            profile != null && profile.getRawMarkdown() != null && !profile.getRawMarkdown().isBlank()
                ? profile.getRawMarkdown()
                : structuredText(profile);
        return fromMarkdown(text);
    }

    /** Renders arbitrary (Markdown-ish) text into a simple paginated PDF. */
    public byte[] fromMarkdown(String markdown) {
        String text = (markdown == null || markdown.isBlank()) ? "CV" : markdown;
        try (PDDocument doc = new PDDocument()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDRectangle pageSize = PDRectangle.A4;
            float usableWidth = pageSize.getWidth() - 2 * MARGIN;
            List<String> lines = wrap(sanitize(text), font, usableWidth);

            int idx = 0;
            do {
                PDPage page = new PDPage(pageSize);
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(font, FONT_SIZE);
                    cs.setLeading(LEADING);
                    cs.newLineAtOffset(MARGIN, pageSize.getHeight() - MARGIN);
                    float y = pageSize.getHeight() - MARGIN;
                    while (idx < lines.size() && y > MARGIN) {
                        cs.showText(lines.get(idx));
                        cs.newLine();
                        y -= LEADING;
                        idx++;
                    }
                    cs.endText();
                }
            } while (idx < lines.size());

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to render résumé PDF", e);
        }
    }

    private String structuredText(CandidateProfile p) {
        if (p == null) {
            return "CV";
        }
        StringBuilder sb = new StringBuilder();
        if (p.getFullName() != null) sb.append(p.getFullName()).append('\n');
        if (p.getEmail() != null) sb.append(p.getEmail()).append('\n');
        if (p.getLocation() != null) sb.append(p.getLocation()).append('\n');
        sb.append('\n');
        if (p.getSkills() != null) sb.append("Compétences\n").append(p.getSkills()).append("\n\n");
        if (p.getExperiences() != null) sb.append("Expériences\n").append(p.getExperiences()).append("\n\n");
        if (p.getEducation() != null) sb.append("Formation\n").append(p.getEducation()).append("\n\n");
        if (p.getCertifications() != null) sb.append("Certifications\n").append(p.getCertifications()).append("\n\n");
        if (p.getLanguages() != null) sb.append("Langues\n").append(p.getLanguages()).append('\n');
        return sb.toString();
    }

    /** Splits on newlines then word-wraps each line to the usable width, hard-splitting long words. */
    private List<String> wrap(String text, PDType1Font font, float maxWidth) {
        List<String> out = new ArrayList<>();
        for (String rawLine : text.split("\n", -1)) {
            if (rawLine.isEmpty()) {
                out.add("");
                continue;
            }
            StringBuilder current = new StringBuilder();
            for (String word : rawLine.split(" ")) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (textWidth(font, candidate) <= maxWidth) {
                    current.setLength(0);
                    current.append(candidate);
                } else {
                    if (!current.isEmpty()) {
                        out.add(current.toString());
                        current.setLength(0);
                    }
                    // Word itself too long: hard-split by character.
                    String remaining = word;
                    while (textWidth(font, remaining) > maxWidth && remaining.length() > 1) {
                        int cut = remaining.length();
                        while (cut > 1 && textWidth(font, remaining.substring(0, cut)) > maxWidth) {
                            cut--;
                        }
                        out.add(remaining.substring(0, cut));
                        remaining = remaining.substring(cut);
                    }
                    current.append(remaining);
                }
            }
            out.add(current.toString());
        }
        return out;
    }

    private float textWidth(PDType1Font font, String s) {
        try {
            return (font.getStringWidth(s) / 1000) * FONT_SIZE;
        } catch (IOException e) {
            return Float.MAX_VALUE;
        }
    }

    /** Keeps only WinAnsi-safe characters (Helvetica), normalizing common typographic ones to ASCII. */
    private String sanitize(String text) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFC);
        StringBuilder sb = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            switch (c) {
                case '‘', '’', '‛' -> sb.append('\'');
                case '“', '”' -> sb.append('"');
                case '–', '—' -> sb.append('-');
                case '…' -> sb.append("...");
                case ' ' -> sb.append(' ');
                case '\t' -> sb.append("    ");
                case '\n' -> sb.append('\n');
                default -> {
                    if ((c >= 0x20 && c <= 0x7E) || (c >= 0xA1 && c <= 0xFF)) {
                        sb.append(c);
                    } else if (c != '\r') {
                        sb.append(' ');
                    }
                }
            }
        }
        return sb.toString();
    }
}
