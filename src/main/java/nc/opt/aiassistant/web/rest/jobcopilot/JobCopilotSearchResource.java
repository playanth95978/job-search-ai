package nc.opt.aiassistant.web.rest.jobcopilot;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.CandidateProfile;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.repository.jobcopilot.CandidateProfileRepository;
import nc.opt.aiassistant.security.SecurityUtils;
import nc.opt.aiassistant.service.criteria.JobOfferCriteria;
import nc.opt.aiassistant.service.dto.JobOfferDTO;
import nc.opt.aiassistant.service.jobcopilot.JobOfferEnrichmentService;
import nc.opt.aiassistant.service.jobcopilot.ats.JobOfferQueryService;
import nc.opt.aiassistant.service.jobcopilot.ats.JobSearchService;
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
    private final JobMatchingService matchingService;
    private final CandidateProfileRepository profileRepository;
    private final JobOfferEnrichmentService jobOfferEnrichmentService;
    private final ObjectMapper objectMapper;
    private final JobOfferQueryService jobOfferQueryService;

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

    @GetMapping("/search")
    public ResponseEntity<List<JobOffer>> search(
        @RequestParam(required = false) String keywords,
        @RequestParam(required = false) String location,
        @RequestParam(required = false) String source
    ) {
        // Embeddings are not triggered here: full-description offers are queued as PENDING and
        // indexed by the background poller, while teaser-only (STUB) offers are enriched lazily
        // on click via /{id}/enrich.
        List<JobOffer> offers = jobSearchService.searchAll(keywords, location, source);
        return ResponseEntity.ok(offers);
    }

    /**
     * {@code POST /jobs/{id}/enrich} : fetches the full description of a list (STUB) offer from its
     * source detail page and queues it for embedding. Idempotent: already-enriched offers are
     * returned unchanged. Meant to be called when the user opens an offer.
     */
    @PostMapping("/{id}/enrich")
    public ResponseEntity<JobOffer> enrich(@PathVariable UUID id) {
        return ResponseEntity.ok(jobOfferEnrichmentService.enrich(id));
    }

    @PostMapping("/fetch")
    public ResponseEntity<List<JobOffer>> fetchFromUrl(@RequestParam String url) {
        return ResponseEntity.ok(jobSearchService.fetchFromUrl(url));
    }

    @GetMapping("/match")
    public ResponseEntity<List<MatchResultDto>> matchJobs(@RequestParam(name = "topK", defaultValue = "30") int topK) {
        String userId = SecurityUtils.getCurrentUserLogin().orElseThrow();
        CandidateProfile profile = profileRepository.findTopByUserIdOrderByCreatedAtDesc(userId).orElse(null);
        if (profile == null) {
            throw new BadRequestAlertException("Entity not found", "ENTITY_NAME", "profileNotfound");
        }
        // Pre-filter by semantic (cosine) similarity to the profile instead of scoring every offer,
        // so we no longer return unrelated jobs with a low score. topK is caller-configurable.
        String query = buildSemanticQuery(profile);
        List<JobOffer> offers = jobSearchService.searchSemantic(query, Math.max(1, topK));
        List<MatchResultDto> results = matchingService.matchJobs(profile, offers);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/match-advanced")
    public ResponseEntity<List<MatchResultDto>> matchAdvanced() {
        String userId = SecurityUtils.getCurrentUserLogin().orElseThrow();
        CandidateProfile profile = profileRepository.findByUserId(userId).orElse(null);
        if (profile == null) return ResponseEntity.badRequest().build();

        // Build a semantic query from profile
        String query = buildSemanticQuery(profile);

        // Perform hybrid search with limit 30
        List<JobOffer> offers = jobSearchService.searchHybrid(query, 30);

        // Score the results
        List<MatchResultDto> results = matchingService.matchJobs(profile, offers);
        return ResponseEntity.ok(results);
    }

    private String buildSemanticQuery(CandidateProfile profile) {
        StringBuilder sb = new StringBuilder();

        appendJsonList(sb, profile.getPreferredRoles(), "Target Roles: ");
        appendJsonList(sb, profile.getSkills(), "Skills: ");

        if (profile.getRawMarkdown() != null && !profile.getRawMarkdown().isBlank()) {
            String resume = profile.getRawMarkdown();
            sb.append("Background: ").append(resume, 0, Math.min(resume.length(), 1000));
        }

        String query = sb.toString().trim();
        if (query.isBlank()) {
            log.warn("Semantic query is empty for user {} — profile has no roles, skills or rawMarkdown", profile.getUserId());
        } else {
            log.info("Semantic query built for user {}: '{}'", profile.getUserId(), query.substring(0, Math.min(query.length(), 200)));
        }
        return query;
    }

    private void appendJsonList(StringBuilder sb, String json, String prefix) {
        if (json == null || json.isBlank()) return;
        try {
            List<String> list;
            if (json.trim().startsWith("[{\"")) {
                // New structured format (SkillDto, etc.)
                if (prefix.toLowerCase().contains("skills")) {
                    List<nc.opt.aiassistant.service.jobcopilot.dto.SkillDto> skillList = objectMapper.readValue(
                        json,
                        new com.fasterxml.jackson.core.type.TypeReference<>() {}
                    );
                    list = skillList.stream().map(nc.opt.aiassistant.service.jobcopilot.dto.SkillDto::name).toList();
                } else {
                    // Fallback or handle other objects if needed
                    list = List.of();
                }
            } else {
                // Old simple string list
                list = objectMapper.readValue(json, new TypeReference<>() {});
            }

            if (!list.isEmpty()) {
                sb.append(prefix).append(String.join(", ", list)).append(". ");
            }
        } catch (Exception e) {
            // Ignore parse errors
        }
    }

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
