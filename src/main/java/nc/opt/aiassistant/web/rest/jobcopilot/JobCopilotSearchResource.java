package nc.opt.aiassistant.web.rest.jobcopilot;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.CandidateProfile;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.repository.jobcopilot.CandidateProfileRepository;
import nc.opt.aiassistant.repository.jobcopilot.JobOfferRepository;
import nc.opt.aiassistant.security.SecurityUtils;
import nc.opt.aiassistant.service.criteria.JobOfferCriteria;
import nc.opt.aiassistant.service.dto.JobOfferDTO;
import nc.opt.aiassistant.service.jobcopilot.JobOfferEnrichmentService;
import nc.opt.aiassistant.service.jobcopilot.JobOfferTranslationService;
import nc.opt.aiassistant.service.jobcopilot.QueryComplexity;
import nc.opt.aiassistant.service.jobcopilot.ats.AtsBoardSuggestionService;
import nc.opt.aiassistant.service.jobcopilot.ats.JobOfferQueryService;
import nc.opt.aiassistant.service.jobcopilot.ats.JobSearchService;
import nc.opt.aiassistant.service.jobcopilot.dto.BoardSuggestionDto;
import nc.opt.aiassistant.service.jobcopilot.dto.MatchResultDto;
import nc.opt.aiassistant.service.jobcopilot.matching.JobMatchingService;
import nc.opt.aiassistant.web.rest.errors.BadRequestAlertException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.PaginationUtil;

@RestController
@RequestMapping("/api/job-copilot/jobs")
@RequiredArgsConstructor
@Slf4j
public class JobCopilotSearchResource {

    private final JobSearchService jobSearchService;
    private final AtsBoardSuggestionService boardSuggestionService;
    private final JobMatchingService matchingService;
    private final CandidateProfileRepository profileRepository;
    private final JobOfferEnrichmentService jobOfferEnrichmentService;
    private final nc.opt.aiassistant.service.jobcopilot.CandidateProfileEmbeddingService profileEmbeddingService;
    private final JobOfferQueryService jobOfferQueryService;
    private final nc.opt.aiassistant.service.jobcopilot.ats.JobOfferFacetService jobOfferFacetService;
    private final JobOfferTranslationService jobOfferTranslationService;
    private final JobOfferRepository jobOfferRepository;
    private final nc.opt.aiassistant.service.jobcopilot.SearchEventLogService searchEventLogService;

    /**
     * {@code GET  /jobs/_search} : query persisted job offers using JHipster criteria filters
     * (e.g. {@code applyUrl.equals=...}). Used to resolve a job offer (and its id) from a preview.
     *
     * @param criteria the filtering criteria bound from request params.
     * @param pageable the pagination information.
     * @return the {@link ResponseEntity} with the matching {@link JobOfferDTO} list.
     */
    @GetMapping("/_search")
    public ResponseEntity<List<JobOfferDTO>> searchByCriteria(
        @org.springdoc.core.annotations.ParameterObject JobOfferCriteria criteria,
        @org.springdoc.core.annotations.ParameterObject Pageable pageable
    ) {
        Page<JobOfferDTO> page = jobOfferQueryService.findByCriteria(criteria, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET /jobs/_facets} : the filter values available under the same criteria as
     * {@link #searchByCriteria}, with their counts. The client holds a single page of results, so it
     * cannot count anything itself; these counts are what the filter panel lists.
     *
     * @param criteria the filtering criteria bound from request params, identical to {@code /_search}.
     */
    @GetMapping("/_facets")
    public ResponseEntity<nc.opt.aiassistant.service.jobcopilot.dto.OfferFacetsDto> facets(
        @org.springdoc.core.annotations.ParameterObject JobOfferCriteria criteria
    ) {
        return ResponseEntity.ok(jobOfferFacetService.facets(criteria));
    }

    @GetMapping("/search")
    public ResponseEntity<List<JobOffer>> search(
        @RequestParam(required = false) String keywords,
        @RequestParam(required = false) String location,
        @RequestParam(required = false) String source,
        @RequestParam(required = false) String country
    ) {
        // Embeddings are not triggered here: full-description offers are queued as PENDING and
        // indexed by the background poller, while teaser-only (STUB) offers are enriched lazily
        // on click via /{id}/enrich. country selects the Adzuna market (fr, gb, us, au, de, ca…).
        List<JobOffer> offers = jobSearchService.searchAll(keywords, location, source, country);
        return ResponseEntity.ok(offers);
    }

    /**
     * {@code GET /jobs/search-smart} : semantic search over the already-ingested corpus using the
     * hybrid pipeline (pgvector nearest-neighbours + BM25, fused with Reciprocal Rank Fusion, then
     * cross-encoder reranked). Unlike {@code /search} it does not hit external boards live — it ranks
     * the mass-ingested offers, so it is instant and understands meaning, not just keywords.
     *
     * @param query    the natural-language query.
     * @param location optional location, applied as a filter on both retrieval arms.
     * @param source   optional {@link nc.opt.aiassistant.domain.enumeration.JobSource} name to restrict results.
     * @param limit    maximum number of offers to return.
     */
    @GetMapping("/search-smart")
    public ResponseEntity<List<JobOffer>> searchSmart(
        @RequestParam String query,
        @RequestParam(required = false) String location,
        @RequestParam(required = false) String source,
        @RequestParam(defaultValue = "30") int limit
    ) {
        // La localisation part en filtre, pas dans le texte de la requete : concatenee, elle se noie
        // parmi les mots frequents et le bras BM25 remonte le bon metier au mauvais endroit.
        List<JobOffer> offers = jobSearchService.searchHybrid(query, location, Math.max(1, limit));
        if (source != null && !source.isBlank()) {
            offers = offers
                .stream()
                .filter(o -> o.getSource() != null && o.getSource().name().equalsIgnoreCase(source))
                .toList();
        }
        return ResponseEntity.ok(offers);
    }

    /**
     * {@code POST /jobs/{id}/enrich} : fetches the full description of a list (STUB) offer from its
     * source detail page and queues it for embedding. Idempotent: already-enriched offers are
     * returned unchanged. Meant to be called when the user opens an offer.
     */
    @PostMapping("/{id}/enrich")
    public ResponseEntity<JobOffer> enrich(@PathVariable UUID id) {
        // Ouvrir une offre est le signal de pertinence le plus abondant : on le capte ici plutot que
        // de demander un appel supplementaire au front, puisque cet endpoint est deja appele au clic.
        searchEventLogService.recordClick(id, currentUserOrNull());
        return ResponseEntity.ok(jobOfferEnrichmentService.enrich(id));
    }

    /**
     * {@code POST /jobs/{id}/click} : enregistre l'ouverture d'une offre deja enrichie.
     *
     * <p>Complete {@code /enrich}, qui n'est appele que pour les offres encore a l'etat de teaser :
     * sans cet endpoint le journal ne verrait que les clics sur les offres incompletes, un biais qui
     * fausserait l'entrainement.
     */
    @PostMapping("/{id}/click")
    public ResponseEntity<Void> click(@PathVariable UUID id) {
        searchEventLogService.recordClick(id, currentUserOrNull());
        return ResponseEntity.noContent().build();
    }

    /** Null pour une recherche anonyme : /search-smart est ouvert, le journal doit l'accepter. */
    private String currentUserOrNull() {
        return SecurityUtils.getCurrentUserLogin().orElse(null);
    }

    @PostMapping("/{id}/translate")
    public ResponseEntity<JobOffer> translate(@PathVariable UUID id, @RequestParam String language) {
        JobOffer offer = jobOfferRepository.findById(id).orElseThrow();
        return ResponseEntity.ok(jobOfferTranslationService.translate(offer, language));
    }

    @PostMapping("/translate")
    public ResponseEntity<JobOffer> translate(@RequestBody JobOffer offer, @RequestParam String language) {
        return ResponseEntity.ok(jobOfferTranslationService.translate(offer, language));
    }

    /**
     * {@code GET /jobs/boards/suggestions} : ATS boards worth watching for the signed-in candidate.
     * Company names come from the model, but each entry is verified against the real ATS API before
     * being returned — the UI never shows a slug that does not exist.
     */
    @GetMapping("/boards/suggestions")
    public ResponseEntity<List<BoardSuggestionDto>> suggestBoards(@RequestParam(defaultValue = "6") int limit) {
        String userId = SecurityUtils.getCurrentUserLogin().orElseThrow();
        return ResponseEntity.ok(boardSuggestionService.suggestFor(userId, limit));
    }

    @PostMapping("/fetch")
    public ResponseEntity<List<JobOffer>> fetchFromUrl(@RequestParam String url) {
        return ResponseEntity.ok(jobSearchService.fetchFromUrl(url));
    }

    @GetMapping("/match")
    public ResponseEntity<List<MatchResultDto>> matchJobs(
        @RequestParam(name = "topK", defaultValue = "30") int topK,
        @RequestParam(required = false) String location
    ) {
        String userId = SecurityUtils.getCurrentUserLogin().orElseThrow();
        CandidateProfile profile = profileRepository.findTopByUserIdOrderByCreatedAtDesc(userId).orElse(null);
        if (profile == null) {
            throw new BadRequestAlertException("Entity not found", "ENTITY_NAME", "profileNotfound");
        }
        // Pre-filter by semantic (cosine) similarity to the profile instead of scoring every offer,
        // so we no longer return unrelated jobs with a low score. topK is caller-configurable.
        String query = profileEmbeddingService.buildProfileText(profile);
        List<JobOffer> offers = jobSearchService.searchSemantic(query, Math.max(1, topK));
        // Optional search-box location biases the location score toward where the user is looking.
        List<MatchResultDto> results = matchingService.matchJobs(profile, offers, location);
        return ResponseEntity.ok(results);
    }

    /**
     * {@code GET /jobs/match-smart} : la recherche IA du candidat, en un seul appel.
     *
     * <p>Remplace le choix que l'utilisateur devait faire entre « Match My Profile » et « Match
     * Advanced » : deux boutons pour deux moteurs, alors que rien dans l'interface ne disait lequel
     * convenait à quoi. {@link QueryComplexity} tranche à sa place — le reranker ONNX n'est payé que
     * lorsque la requête porte assez de contraintes pour qu'il change le classement.
     *
     * <p>Les mots de l'utilisateur priment sur le profil quand il en saisit : c'est ce qu'il cherche
     * <em>maintenant</em>. Sans eux, le profil devient la requête, et l'écran se comporte comme
     * l'ancien « match ». Le scoring, lui, part toujours du profil : c'est ce qui distingue cet
     * écran d'une recherche plein texte.
     *
     * @param query    ce que l'utilisateur a tapé ; vide pour partir du seul profil.
     * @param location filtre optionnel, appliqué en filtre et jamais concaténé à la requête.
     */
    @GetMapping("/match-smart")
    public ResponseEntity<List<MatchResultDto>> matchSmart(
        @RequestParam(required = false) String query,
        @RequestParam(required = false) String location,
        @RequestParam(name = "topK", defaultValue = "30") int topK
    ) {
        String userId = SecurityUtils.getCurrentUserLogin().orElseThrow();
        CandidateProfile profile = profileRepository.findTopByUserIdOrderByCreatedAtDesc(userId).orElse(null);
        if (profile == null) {
            throw new BadRequestAlertException("Entity not found", "ENTITY_NAME", "profileNotfound");
        }

        boolean hasUserWords = query != null && !query.isBlank();
        String retrievalQuery = hasUserWords ? query.trim() : profileEmbeddingService.buildProfileText(profile);

        // La complexité se juge sur ce que l'utilisateur a écrit. Un texte de profil est long par
        // construction : le mesurer reviendrait à toujours reranker, ce qui annulerait l'arbitrage.
        boolean rerank = hasUserWords && QueryComplexity.needsReranking(query);
        log.debug("match-smart: rerank={} (query='{}')", rerank, query);

        List<JobOffer> offers = jobSearchService.searchHybrid(retrievalQuery, location, Math.max(1, topK), rerank);
        return ResponseEntity.ok(matchingService.matchJobs(profile, offers, location));
    }

    @GetMapping("/match-advanced")
    public ResponseEntity<List<MatchResultDto>> matchAdvanced(@RequestParam(required = false) String location) {
        String userId = SecurityUtils.getCurrentUserLogin().orElseThrow();
        CandidateProfile profile = profileRepository.findByUserId(userId).orElse(null);
        if (profile == null) return ResponseEntity.badRequest().build();

        // Build a semantic query from profile
        String query = profileEmbeddingService.buildProfileText(profile);

        // Perform hybrid search with limit 30
        List<JobOffer> offers = jobSearchService.searchHybrid(query, 30);

        // Score the results — optional search-box location biases the location component.
        List<MatchResultDto> results = matchingService.matchJobs(profile, offers, location);
        return ResponseEntity.ok(results);
    }

    /**
     * {@code GET /jobs/indexed-count} : public count of offers actually indexed (embedding COMPLETED),
     * i.e. searchable by the AI. Used by the public home page as a "corpus size" stat.
     *
     * @return the {@link ResponseEntity} with a body {@code { "count": <long> }}.
     */
    @GetMapping("/indexed-count")
    public ResponseEntity<IndexedCount> indexedCount() {
        long count = jobOfferRepository.countByEmbeddingStatus(nc.opt.aiassistant.domain.enumeration.EmbeddingStatus.COMPLETED);
        return ResponseEntity.ok(new IndexedCount(count));
    }

    /** Lightweight response for {@link #indexedCount()}. */
    public record IndexedCount(long count) {}

    @GetMapping("")
    public ResponseEntity<List<JobOfferDTO>> getAllJobOffers(
        JobOfferCriteria criteria,
        @org.springdoc.core.annotations.ParameterObject Pageable pageable
    ) {
        log.debug("REST request to get JobOffers by criteria: {}", criteria);

        Page<JobOfferDTO> page = jobOfferQueryService.findByCriteria(criteria, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }
}
