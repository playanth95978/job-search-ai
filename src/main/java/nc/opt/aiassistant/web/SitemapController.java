package nc.opt.aiassistant.web;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import nc.opt.aiassistant.config.ApplicationProperties;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves {@code /sitemap.xml} for search engines, listing the public, indexable pages of the SPA.
 * <p>
 * The base URL comes from {@code application.seo.base-url}. Private/app routes (dashboard, account,
 * admin, chat, onboarding…) are intentionally excluded — they are auth-gated and marked
 * {@code noindex} client-side and in robots.txt.
 */
@RestController
public class SitemapController {

    /** Public, indexable SPA routes and their crawl hints. Keep in sync with robots.txt and the Angular routes. */
    private record Page(String path, String changefreq, String priority) {}

    private static final List<Page> PAGES = List.of(
        new Page("/", "daily", "1.0"),
        new Page("/search", "daily", "0.9"),
        new Page("/a-propos", "monthly", "0.6"),
        new Page("/contact", "monthly", "0.5"),
        new Page("/faq", "monthly", "0.6"),
        new Page("/legal/conditions-utilisation", "yearly", "0.2"),
        new Page("/legal/confidentialite", "yearly", "0.2"),
        new Page("/legal/cookies", "yearly", "0.2"),
        new Page("/legal/mentions-legales", "yearly", "0.2")
    );

    private final String baseUrl;

    public SitemapController(ApplicationProperties applicationProperties) {
        // Normalise: no trailing slash so we can concatenate paths safely.
        String configured = applicationProperties.getSeo().getBaseUrl();
        this.baseUrl = configured.endsWith("/") ? configured.substring(0, configured.length() - 1) : configured;
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String sitemap() {
        String lastmod = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        StringBuilder xml = new StringBuilder(1024);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        for (Page page : PAGES) {
            xml.append("  <url>\n")
                .append("    <loc>")
                .append(baseUrl)
                .append(page.path())
                .append("</loc>\n")
                .append("    <lastmod>")
                .append(lastmod)
                .append("</lastmod>\n")
                .append("    <changefreq>")
                .append(page.changefreq())
                .append("</changefreq>\n")
                .append("    <priority>")
                .append(page.priority())
                .append("</priority>\n")
                .append("  </url>\n");
        }
        xml.append("</urlset>\n");
        return xml.toString();
    }
}
