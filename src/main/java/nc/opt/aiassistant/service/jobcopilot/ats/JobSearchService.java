package nc.opt.aiassistant.service.jobcopilot.ats;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.repository.jobcopilot.JobOfferRepository;
import nc.opt.aiassistant.service.jobcopilot.JobOfferIngestionService;
import nc.opt.aiassistant.service.jobcopilot.JobOfferSearchService;
import nc.opt.aiassistant.service.jobcopilot.geo.GeoLocation;
import nc.opt.aiassistant.service.jobcopilot.geo.GeoLocationService;
import nc.opt.aiassistant.service.jobcopilot.geo.GeoMatchLevel;
import nc.opt.aiassistant.service.jobcopilot.geo.GeoScore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobSearchService {

    private final List<AtsConnector> connectors;
    private final JobOfferRepository jobOfferRepository;
    private final JobOfferIngestionService jobOfferIngestionService;
    private final JobOfferSearchService jobOfferSearchService;
    private final GeoLocationService geoLocationService;
    private final AtsBoardResolver boardResolver;

    /** Searches all configured connectors and persists new offers with semantic enrichment. */
    @Transactional
    public List<JobOffer> searchAll(String keywords, String location) {
        return searchAll(keywords, location, null, null);
    }

    @Transactional
    public List<JobOffer> searchAll(String keywords, String location, String source) {
        return searchAll(keywords, location, source, null);
    }

    /**
     * Searches the configured connectors and persists new offers with semantic enrichment.
     *
     * @param source optional {@link nc.opt.aiassistant.domain.enumeration.JobSource} name (e.g. {@code FRANCE_TRAVAIL})
     *               to restrict the search to a single connector; when {@code null}/blank all connectors are queried.
     */
    @Transactional
    public List<JobOffer> searchAll(String keywords, String location, String source, String country) {
        List<JobOffer> allOffers = new ArrayList<>();
        for (AtsConnector connector : connectors) {
            if (source != null && !source.isBlank() && !connector.getSource().name().equalsIgnoreCase(source)) {
                continue;
            }
            try {
                List<JobOffer> offers = connector.fetchJobs(keywords, location, country);
                allOffers.addAll(offers);
            } catch (Exception e) {
                log.error("Connector {} failed: {}", connector.getSource(), e.getMessage());
            }
        }
        allOffers.forEach(jobOfferIngestionService::ingest);
        // Connectors filter by location inconsistently (some ignore it, some match loosely), so
        // re-rank the aggregated results by geographic relevance to the searched location and drop
        // confident wrong-country hits — remote and unknown offers are kept.
        return rerankByLocation(allOffers, location);
    }

    /**
     * Re-orders offers by how well their location matches {@code searchLocation} (via the GeoLocation
     * agent) and removes confident mismatches. No-op when the search location is blank or unresolvable.
     */
    private List<JobOffer> rerankByLocation(List<JobOffer> offers, String searchLocation) {
        if (searchLocation == null || searchLocation.isBlank() || offers.isEmpty()) {
            return offers;
        }
        GeoLocation ref = geoLocationService.resolve(searchLocation, true);
        if (!ref.usable()) {
            return offers; // couldn't understand the searched location — leave order untouched
        }

        record Scored(JobOffer offer, double score, GeoMatchLevel level) {}
        List<Scored> scored = offers
            .stream()
            .map(o -> {
                GeoScore gs = geoLocationService.score(ref, geoLocationService.resolve(o.getLocation(), false));
                return new Scored(o, gs.score(), gs.level());
            })
            .toList();

        List<JobOffer> kept = scored
            .stream()
            .filter(s -> s.level() != GeoMatchLevel.MISMATCH)
            .sorted(Comparator.comparingDouble(Scored::score).reversed())
            .map(Scored::offer)
            .toList();

        long dropped = offers.size() - kept.size();
        if (dropped > 0) {
            log.info("Geo re-rank for '{}': kept {} offers, dropped {} location mismatches", searchLocation, kept.size(), dropped);
        }
        return kept;
    }

    /** Fetches from a specific ATS board URL. */
    @Transactional
    /**
     * Fetches offers from a pasted board/feed URL by delegating to the first connector that
     * {@link AtsConnector#supports(String) supports} it (Greenhouse/Lever/SmartRecruiters boards,
     * or an aggregator feed like RemoteOK / HelloWork / Emploi.nc), then persists them.
     */
    public List<JobOffer> fetchFromUrl(String url) {
        if (url == null || url.isBlank()) return List.of();
        List<JobOffer> offers = connectors
            .stream()
            .filter(c -> c.supports(url))
            .findFirst()
            .map(c -> c.fetchFromUrl(url))
            .orElse(List.of());
        offers.forEach(jobOfferIngestionService::ingest);
        return offers;
    }

    /**
     * Fetches a company's postings from its <b>name</b>: the ATS and its slug are resolved by probing
     * the boards ({@link AtsBoardResolver}), so no URL has to be pasted. Offers are persisted like any
     * other source.
     *
     * @return the resolved board and its offers, or empty when no ATS answered for that company
     */
    @Transactional
    public Optional<AtsBoardResolver.ResolvedBoard> fetchFromCompanyName(String companyName) {
        Optional<AtsBoardResolver.ResolvedBoard> resolved = boardResolver.resolve(companyName);
        resolved.ifPresent(board -> board.offers().forEach(jobOfferIngestionService::ingest));
        return resolved;
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
}
