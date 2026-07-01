package nc.opt.aiassistant.service.jobcopilot.matching;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.CandidateProfile;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.domain.UserPreference;
import nc.opt.aiassistant.repository.jobcopilot.UserPreferenceRepository;
import nc.opt.aiassistant.service.jobcopilot.dto.JobOfferDto;
import nc.opt.aiassistant.service.jobcopilot.dto.MatchResultDto;
import nc.opt.aiassistant.service.jobcopilot.dto.MatchTag;
import nc.opt.aiassistant.service.jobcopilot.dto.MatchTagDto;
import nc.opt.aiassistant.service.jobcopilot.dto.SkillDto;
import nc.opt.aiassistant.service.jobcopilot.geo.GeoLocation;
import nc.opt.aiassistant.service.jobcopilot.geo.GeoLocationService;
import nc.opt.aiassistant.service.jobcopilot.geo.GeoScore;
import nc.opt.aiassistant.service.reranker.RerankerService;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class JobMatchingService {

    private final VectorStore vectorStore;
    private final RerankerService rerankerService;
    private final UserPreferenceRepository preferenceRepository;
    private final ObjectMapper objectMapper;
    private final GeoLocationService geoLocationService;

    public JobMatchingService(
        @Qualifier("pdfVectorStore") VectorStore pdfVectorStore,
        RerankerService rerankerService,
        UserPreferenceRepository preferenceRepository,
        ObjectMapper objectMapper,
        GeoLocationService geoLocationService
    ) {
        this.vectorStore = pdfVectorStore;
        this.rerankerService = rerankerService;
        this.preferenceRepository = preferenceRepository;
        this.objectMapper = objectMapper;
        this.geoLocationService = geoLocationService;
    }

    /**
     * Matches a candidate profile against a list of job offers using hybrid scoring.
     *
     * <p>The {@code offers} list <strong>must</strong> be ordered by retrieval relevance
     * (cosine for "match", reranked for "match-advanced"): the position drives the semantic
     * relevance component of the score, which is what differentiates offers — and the two
     * search modes — once structured metadata (skills/salary/…) is sparse.
     */
    public List<MatchResultDto> matchJobs(CandidateProfile profile, List<JobOffer> offers) {
        return matchJobs(profile, offers, null);
    }

    /**
     * Same as {@link #matchJobs(CandidateProfile, List)} but with an explicit {@code searchLocation}
     * (e.g. from the search box): when provided and resolvable, it becomes the sole location reference,
     * overriding the CV / preferred locations so results lean toward where the user is looking now.
     */
    public List<MatchResultDto> matchJobs(CandidateProfile profile, List<JobOffer> offers, String searchLocation) {
        Set<String> candidateSkills = parseSkills(profile.getSkills());
        UserPreference prefs = preferenceRepository.findByUserId(profile.getUserId()).orElse(null);
        // Resolve the reference locations once (LLM allowed here — at most a couple of strings per
        // request), then score each offer's location against them deterministically in the loop.
        List<GeoLocation> referenceGeos = resolveReferenceGeos(profile, prefs, searchLocation);

        int total = offers.size();
        List<MatchResultDto> scored = new ArrayList<>(total);
        for (int rank = 0; rank < total; rank++) {
            double relevance = rankRelevance(rank, total);
            scored.add(scoreOffer(profile, candidateSkills, offers.get(rank), prefs, relevance, referenceGeos));
        }
        return scored.stream().sorted(Comparator.comparingDouble(MatchResultDto::score).reversed()).toList();
    }

    /**
     * Resolves the location references for a match run (once). An explicit {@code searchLocation} wins
     * outright; otherwise the candidate's CV location plus any preferred locations are used.
     */
    private List<GeoLocation> resolveReferenceGeos(CandidateProfile profile, UserPreference prefs, String searchLocation) {
        if (searchLocation != null && !searchLocation.isBlank()) {
            GeoLocation searched = geoLocationService.resolve(searchLocation, true);
            if (searched.usable()) {
                return List.of(searched);
            }
        }
        List<GeoLocation> refs = new ArrayList<>();
        if (profile.getLocation() != null && !profile.getLocation().isBlank()) {
            refs.add(geoLocationService.resolve(profile.getLocation(), true));
        }
        if (prefs != null && prefs.getPreferredLocations() != null) {
            for (String loc : parseList(prefs.getPreferredLocations())) {
                if (loc != null && !loc.isBlank()) {
                    refs.add(geoLocationService.resolve(loc, true));
                }
            }
        }
        return refs.stream().filter(GeoLocation::usable).toList();
    }

    /**
     * Maps a 0-based retrieval rank to a continuous relevance in {@code [0.4, 1.0]}: the top hit
     * scores 1.0 and decays linearly to 0.4 for the last hit. Keeping a 0.4 floor (rather than 0)
     * avoids punishing a still-relevant tail, while the spread breaks the "everything is 50%" tie.
     */
    private double rankRelevance(int rank, int total) {
        if (total <= 1) return 1.0;
        return 1.0 - 0.6 * ((double) rank / (total - 1));
    }

    /** Performs semantic search for job offers matching the candidate CV chunks. */
    public List<Document> semanticSearch(String query, int topK) {
        SearchRequest request = SearchRequest.builder().query(query).topK(topK).build();
        List<Document> results = vectorStore.similaritySearch(request);
        return rerankerService.rerank(query, results, topK);
    }

    MatchResultDto scoreOffer(
        CandidateProfile profile,
        Set<String> candidateSkills,
        JobOffer offer,
        UserPreference prefs,
        double relevance,
        List<GeoLocation> referenceGeos
    ) {
        Set<String> jobSkills = parseSkills(offer.getSkills());
        List<MatchTagDto> strengths = new ArrayList<>();
        List<MatchTagDto> weaknesses = new ArrayList<>();

        double skillScore = computeSkillScore(candidateSkills, jobSkills, strengths, weaknesses);
        double locationScore = computeLocationScore(referenceGeos, offer, strengths, weaknesses);
        double remoteScore = computeRemoteScore(offer, prefs, strengths, weaknesses);
        double salaryScore = computeSalaryScore(offer, prefs, strengths, weaknesses);

        if (relevance >= 0.8) {
            strengths.add(MatchTagDto.of(MatchTag.STRONG_MATCH));
        }

        // Semantic relevance dominates: it is the most reliable signal when offers carry little
        // structured metadata (skills/salary often absent), where the heuristics all collapse to 0.5.
        double finalScore = relevance * 0.45 + skillScore * 0.35 + locationScore * 0.10 + remoteScore * 0.05 + salaryScore * 0.05;

        return new MatchResultDto(toDto(offer), Math.round(finalScore * 100.0) / 100.0, strengths, weaknesses);
    }

    private double computeSkillScore(Set<String> candidate, Set<String> job, List<MatchTagDto> s, List<MatchTagDto> w) {
        if (job.isEmpty()) return 0.5;
        long matched = job.stream().filter(candidate::contains).count();
        job.stream()
            .filter(candidate::contains)
            .forEach(sk -> s.add(MatchTagDto.of(MatchTag.MATCHED_SKILL, sk)));
        job.stream()
            .filter(sk -> !candidate.contains(sk))
            .forEach(sk -> w.add(MatchTagDto.of(MatchTag.MISSING_SKILL, sk)));
        return (double) matched / job.size();
    }

    /**
     * Proximity-aware location score via the GeoLocation agent: exact city 1.0, same Grand Nouméa
     * agglomeration 0.9, same province/region 0.7, same country 0.5, remote 0.85, unknown 0.4,
     * different country 0.15. Falls back to a neutral 0.5 when the candidate has no usable location.
     */
    private double computeLocationScore(List<GeoLocation> referenceGeos, JobOffer offer, List<MatchTagDto> s, List<MatchTagDto> w) {
        if (referenceGeos.isEmpty()) return 0.5;
        GeoLocation offerGeo = geoLocationService.resolve(offer.getLocation(), false);
        GeoScore best = geoLocationService.bestScore(referenceGeos, offerGeo);
        switch (best.level()) {
            case EXACT, AGGLOMERATION -> s.add(MatchTagDto.of(MatchTag.LOCATION_MATCH));
            case REGION -> s.add(MatchTagDto.of(MatchTag.PREFERRED_LOCATION));
            case MISMATCH -> w.add(MatchTagDto.of(MatchTag.LOCATION_MISMATCH));
            default -> {
                /* COUNTRY / REMOTE / UNKNOWN: neutral, no tag */
            }
        }
        return best.score();
    }

    private double computeRemoteScore(JobOffer offer, UserPreference prefs, List<MatchTagDto> s, List<MatchTagDto> w) {
        if (prefs == null || prefs.getRemoteOnly() == null || !prefs.getRemoteOnly()) return 0.5;
        if (Boolean.TRUE.equals(offer.getRemote())) {
            s.add(MatchTagDto.of(MatchTag.REMOTE));
            return 1.0;
        }
        w.add(MatchTagDto.of(MatchTag.NOT_REMOTE));
        return 0.0;
    }

    private double computeSalaryScore(JobOffer offer, UserPreference prefs, List<MatchTagDto> s, List<MatchTagDto> w) {
        if (prefs == null || prefs.getSalaryMin() == null || offer.getSalaryMax() == null) return 0.5;
        if (offer.getSalaryMax() >= prefs.getSalaryMin()) {
            s.add(MatchTagDto.of(MatchTag.SALARY_COMPATIBLE));
            return 1.0;
        }
        w.add(MatchTagDto.of(MatchTag.SALARY_BELOW));
        return 0.2;
    }

    private Set<String> parseSkills(String json) {
        if (json == null || json.isBlank()) return Set.of();
        try {
            // Check if it's the new structured format or the old string list
            if (json.trim().startsWith("[{\"")) {
                List<SkillDto> list = objectMapper.readValue(json, new TypeReference<>() {});
                return list
                    .stream()
                    .map(SkillDto::name)
                    .filter(java.util.Objects::nonNull)
                    .map(String::toLowerCase)
                    .collect(Collectors.toSet());
            } else {
                List<String> list = objectMapper.readValue(json, new TypeReference<>() {});
                return list.stream().map(String::toLowerCase).collect(Collectors.toSet());
            }
        } catch (Exception e) {
            log.warn("Failed to parse skills JSON: {}", json);
            return Set.of();
        }
    }

    private List<String> parseList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private JobOfferDto toDto(JobOffer o) {
        return new JobOfferDto(
            o.getId(),
            o.getTitle(),
            o.getCompany(),
            o.getLocation(),
            Boolean.TRUE.equals(o.getRemote()),
            o.getDescription(),
            parseList(o.getSkills()),
            o.getSource() != null ? o.getSource().name() : null,
            o.getApplyUrl(),
            o.getSalaryMin(),
            o.getSalaryMax(),
            o.getContractType()
        );
    }
}
