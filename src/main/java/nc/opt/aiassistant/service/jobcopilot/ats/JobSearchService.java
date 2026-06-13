package nc.opt.aiassistant.service.jobcopilot.ats;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.repository.jobcopilot.JobOfferRepository;
import nc.opt.aiassistant.service.jobcopilot.JobOfferIngestionService;
import nc.opt.aiassistant.service.jobcopilot.JobOfferSearchService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobSearchService {

    private final List<AtsConnector> connectors;
    private final JobOfferRepository jobOfferRepository;
    private final GreenhouseConnector greenhouseConnector;
    private final LeverConnector leverConnector;
    private final JobOfferIngestionService jobOfferIngestionService;
    private final JobOfferSearchService jobOfferSearchService;

    /** Searches all configured connectors and persists new offers with semantic enrichment. */
    @Transactional
    public List<JobOffer> searchAll(String keywords, String location) {
        return searchAll(keywords, location, null);
    }

    /**
     * Searches the configured connectors and persists new offers with semantic enrichment.
     *
     * @param source optional {@link nc.opt.aiassistant.domain.enumeration.JobSource} name (e.g. {@code FRANCE_TRAVAIL})
     *               to restrict the search to a single connector; when {@code null}/blank all connectors are queried.
     */
    @Transactional
    public List<JobOffer> searchAll(String keywords, String location, String source) {
        List<JobOffer> allOffers = new ArrayList<>();
        for (AtsConnector connector : connectors) {
            if (source != null && !source.isBlank() && !connector.getSource().name().equalsIgnoreCase(source)) {
                continue;
            }
            try {
                List<JobOffer> offers = connector.fetchJobs(keywords, location);
                allOffers.addAll(offers);
            } catch (Exception e) {
                log.error("Connector {} failed: {}", connector.getSource(), e.getMessage());
            }
        }
        allOffers.forEach(jobOfferIngestionService::ingest);
        return allOffers;
    }

    /** Fetches from a specific ATS board URL. */
    @Transactional
    public List<JobOffer> fetchFromUrl(String url) {
        List<JobOffer> offers = new ArrayList<>();
        if (url.contains("greenhouse.io")) {
            String board = extractBoardToken(url, "greenhouse.io/");
            offers.addAll(greenhouseConnector.fetchFromBoard(board));
        } else if (url.contains("lever.co")) {
            String company = extractBoardToken(url, "lever.co/");
            offers.addAll(leverConnector.fetchFromCompany(company));
        }
        offers.forEach(jobOfferIngestionService::ingest);
        return offers;
    }

    /**
     * Hybrid search using Vector + BM25 + Reranking.
     */
    public List<JobOffer> searchHybrid(String query, int limit) {
        return jobOfferSearchService.searchHybrid(query, limit);
    }

    /**
     * Simple semantic search (cosine distance) returning the {@code topK} most similar offers.
     */
    public List<JobOffer> searchSemantic(String query, int topK) {
        return jobOfferSearchService.searchSemantic(query, topK);
    }

    /** Searches locally persisted job offers by keyword. */
    public List<JobOffer> searchLocal(String keyword) {
        return jobOfferRepository.searchByKeyword(keyword);
    }

    private String extractBoardToken(String url, String marker) {
        int idx = url.indexOf(marker);
        if (idx < 0) return url;
        String rest = url.substring(idx + marker.length());
        int slash = rest.indexOf('/');
        return slash > 0 ? rest.substring(0, slash) : rest;
    }
}
