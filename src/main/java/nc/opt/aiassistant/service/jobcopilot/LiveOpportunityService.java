package nc.opt.aiassistant.service.jobcopilot;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.CandidateProfile;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.repository.jobcopilot.CandidateProfileRepository;
import nc.opt.aiassistant.service.jobcopilot.ats.JobSearchService;
import org.springframework.stereotype.Service;

/**
 * Builds the home page's "latest opportunities for you" feed: derives a search query from the
 * signed-in user's CV profile (target roles first, top skills as fallback, plus location) and runs
 * it live against the external job boards (Adzuna / France Travail) via {@link JobSearchService}.
 * Results are cached per user for a short while, so repeated home visits don't re-hit the boards
 * on every load. Uses the boards directly (not the MCP web-search) because they return structured,
 * reliable job offers rather than generic web links.
 */
@Slf4j
@Service
public class LiveOpportunityService {

    private static final int MAX_RESULTS = 12;

    private final CandidateProfileRepository profileRepository;
    private final CandidateProfileEmbeddingService profileService;
    private final JobSearchService jobSearchService;

    /** Per-user cache of the last live feed; short TTL keeps it fresh without hammering the boards. */
    private final Cache<String, List<JobOffer>> cache = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofHours(2))
        .maximumSize(10_000)
        .build();

    public LiveOpportunityService(
        CandidateProfileRepository profileRepository,
        CandidateProfileEmbeddingService profileService,
        JobSearchService jobSearchService
    ) {
        this.profileRepository = profileRepository;
        this.profileService = profileService;
        this.jobSearchService = jobSearchService;
    }

    /**
     * Latest board opportunities matching the user's profile. Returns an empty list when the user
     * has no CV profile yet (the caller hides the section). When {@code refresh} is true the cache
     * is bypassed and the boards are queried again.
     */
    public List<JobOffer> forUser(String userId, boolean refresh) {
        if (userId == null || userId.isBlank()) return List.of();
        if (!refresh) {
            List<JobOffer> cached = cache.getIfPresent(userId);
            if (cached != null) return cached;
        }
        List<JobOffer> results = fetch(userId);
        cache.put(userId, results);
        return results;
    }

    private List<JobOffer> fetch(String userId) {
        CandidateProfile profile = profileRepository.findTopByUserIdOrderByCreatedAtDesc(userId).orElse(null);
        if (profile == null) return List.of();

        String keywords = buildKeywords(profile);
        if (keywords.isBlank()) return List.of();

        try {
            List<JobOffer> offers = jobSearchService.searchAll(keywords, profile.getLocation());
            return offers.stream().limit(MAX_RESULTS).toList();
        } catch (Exception e) {
            log.warn("Live opportunities fetch failed for user {}: {}", userId, e.getMessage());
            return List.of();
        }
    }

    /** Concise keyword query: target roles first, falling back to top skills. */
    private String buildKeywords(CandidateProfile profile) {
        List<String> roles = profileService.extractPreferredRoles(profile);
        if (!roles.isEmpty()) {
            return String.join(" ", roles.stream().limit(2).toList());
        }
        List<String> skills = profileService.extractSkillNames(profile);
        return String.join(" ", skills.stream().limit(3).toList());
    }
}
