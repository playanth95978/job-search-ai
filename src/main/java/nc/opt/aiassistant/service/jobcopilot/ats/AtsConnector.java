package nc.opt.aiassistant.service.jobcopilot.ats;

import java.util.List;
import nc.opt.aiassistant.domain.CandidateProfile;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.domain.enumeration.JobSource;
import reactor.core.publisher.Flux;

public interface AtsConnector {
    boolean supports(String url);

    JobSource getSource();

    List<JobOffer> fetchJobs(String keywords, String location);

    /**
     * Country-aware fetch. Only connectors whose API is partitioned by country (e.g. Adzuna, whose
     * country is in the URL path) use {@code country}; all others ignore it and fall back to the
     * two-argument form. {@code country} is an ISO-ish market code (fr, gb, us, au, de, ca…).
     */
    default List<JobOffer> fetchJobs(String keywords, String location, String country) {
        return fetchJobs(keywords, location);
    }

    /**
     * Fetches jobs with pagination using Flux (useful for Spring Batch ingestion).
     */
    default Flux<JobOffer> fetchJobsPaginated(String keywords, String location) {
        return Flux.fromIterable(fetchJobs(keywords, location));
    }

    /**
     * Fetches the full offer (notably its complete description) from its detail page.
     * Used to enrich a lightweight list result lazily, one request at a time.
     *
     * @return the enriched offer, or {@code null} if this connector cannot enrich the URL.
     */
    default JobOffer fetchDetail(String url) {
        return null;
    }

    /**
     * Fetches every posting of a company board identified by its slug on <em>this</em> ATS
     * (Greenhouse board token, Lever/SmartRecruiters/Recruitee company, Workable account, Ashby board).
     *
     * <p>Default: none — only board-based connectors implement it. It is what makes a slug probeable
     * without knowing which ATS a company uses: an empty result means "not this one".</p>
     */
    default List<JobOffer> fetchBySlug(String slug) {
        return List.of();
    }

    /**
     * Fetches the offers implied by a board/feed URL this connector {@link #supports(String) supports}
     * — e.g. a Greenhouse/Lever/SmartRecruiters company board, or an aggregator's feed. Default: none.
     */
    default List<JobOffer> fetchFromUrl(String url) {
        return List.of();
    }

    /** Extracts the path segment right after {@code marker} (e.g. the board/company slug in a URL). */
    static String boardSlug(String url, String marker) {
        if (url == null) return "";
        int idx = url.indexOf(marker);
        if (idx < 0) return url;
        String rest = url.substring(idx + marker.length());
        int slash = rest.indexOf('/');
        return slash > 0 ? rest.substring(0, slash) : rest;
    }

    /**
     * Extracts the ATS-specific posting reference from an offer/apply URL, or {@code null} if this
     * connector can't parse it. Sert à récupérer le détail d'une offre d'agrégateur (France Travail,
     * Adzuna…) dont l'{@code applyUrl} pointe vers un board ATS.
     *
     * <p><b>Lecture seule.</b> Aucun connecteur ne soumet de candidature : la soumission
     * automatique a été retirée (CGU des ATS, et consentement du candidat requis pour tout envoi
     * en son nom). Le candidat postule lui-même via {@code applyUrl}.</p>
     */
    default PostingRef parseRef(String url) {
        return null;
    }

    /** A parsed reference to a specific posting on an ATS: {@code site} (company/board slug) + {@code postingId}. */
    record PostingRef(String site, String postingId) {}
}
