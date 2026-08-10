package nc.opt.aiassistant.service.jobcopilot.ats;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.domain.enumeration.JobSource;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HelloWorkConnector implements AtsConnector {

    private static final String SEARCH_URL = "https://www.hellowork.com/fr-fr/emploi/recherche.html";

    @Override
    public boolean supports(String url) {
        return url != null && url.contains("hellowork.com");
    }

    @Override
    public JobSource getSource() {
        return JobSource.HELLOWORK;
    }

    @Override
    public List<JobOffer> fetchFromUrl(String url) {
        // A bare hellowork.com URL maps to a default search (first page).
        return fetchJobs(null, null);
    }

    @Override
    public List<JobOffer> fetchJobs(String keywords, String location) {
        List<JobOffer> results = new ArrayList<>();
        try {
            String url = buildSearchUrl(keywords, location);
            Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/125.0 Safari/537.36")
                .timeout(15_000)
                .get();

            Elements cards = doc.select("[data-testid='offer-card'], .offer-card, article.offer, .tw-card");
            if (cards.isEmpty()) {
                cards = doc.select("a[href*='/emploi/']");
            }

            for (Element card : cards) {
                JobOffer offer = parseCard(card);
                if (offer != null && offer.getTitle() != null && !offer.getTitle().isBlank()) {
                    results.add(offer);
                }
                if (results.size() >= 50) break;
            }
        } catch (Exception e) {
            log.error("HelloWork fetch failed: {}", e.getMessage());
        }
        return results;
    }

    private String buildSearchUrl(String keywords, String location) {
        StringBuilder sb = new StringBuilder(SEARCH_URL).append("?");
        if (keywords != null && !keywords.isBlank()) {
            sb.append("k=").append(encode(keywords));
        }
        if (location != null && !location.isBlank()) {
            sb.append("&l=").append(encode(location));
        }
        return sb.toString();
    }

    private JobOffer parseCard(Element card) {
        try {
            JobOffer offer = new JobOffer();
            String title = extractText(card, "h3, h2, [data-testid='offer-title'], .offer-title");
            if (title.isBlank()) {
                title = card.text().length() > 100 ? card.text().substring(0, 100) : card.text();
            }
            offer.setTitle(title);
            offer.setCompany(extractText(card, "[data-testid='offer-company'], .company-name, .offer-company"));
            offer.setLocation(extractText(card, "[data-testid='offer-location'], .offer-location, .location"));
            offer.setSource(JobSource.HELLOWORK);
            offer.setRemote(isRemote(offer.getLocation(), offer.getTitle()));

            String href = card.tagName().equals("a") ? card.absUrl("href") : "";
            if (href.isBlank()) {
                Element link = card.selectFirst("a[href]");
                if (link != null) href = link.absUrl("href");
            }
            offer.setApplyUrl(href.isBlank() ? null : href);
            offer.setSourceId(href.isBlank() ? null : String.valueOf(href.hashCode()));

            String contractText = extractText(card, "[data-testid='offer-contract'], .contract-type, .badge");
            if (!contractText.isBlank()) offer.setContractType(contractText);

            return offer;
        } catch (Exception e) {
            log.debug("Failed to parse HelloWork card: {}", e.getMessage());
            return null;
        }
    }

    private String extractText(Element parent, String cssSelector) {
        Element el = parent.selectFirst(cssSelector);
        return el != null ? el.text().trim() : "";
    }

    private boolean isRemote(String location, String title) {
        String combined = ((location != null ? location : "") + " " + title).toLowerCase();
        return combined.contains("remote") || combined.contains("télétravail") || combined.contains("teletravail");
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
