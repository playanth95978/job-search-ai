package nc.opt.aiassistant.service.jobcopilot.ats;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.CandidateProfile;
import nc.opt.aiassistant.repository.jobcopilot.CandidateProfileRepository;
import nc.opt.aiassistant.service.jobcopilot.dto.BoardSuggestionDto;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Suggests ATS boards worth watching, from the user's own profile — the "no URL to paste" path.
 *
 * <p>Division of labour: the model only proposes <b>company names</b> (what it is good at, from the
 * candidate's roles, skills and location), and {@link AtsBoardResolver} then proves which ATS hosts
 * each of them by calling the real APIs. A company the model invented, or one with no public board,
 * simply drops out — nothing unverified ever reaches the UI.</p>
 *
 * <p>The suggestion call runs on the small model: it is a short, low-stakes completion, and the
 * free-tier quota is better spent on the conversation itself.</p>
 */
@Slf4j
@Service
public class AtsBoardSuggestionService {

    private static final String PROMPT = """
        You are helping a candidate find companies whose job board is worth watching.

        Candidate profile:
        - roles: {roles}
        - skills: {skills}
        - location: {location}

        List {limit} real companies that hire for this profile and are likely to publish their openings
        on a public ATS board (Greenhouse, Lever, SmartRecruiters, Ashby, Workable, Recruitee).
        Prefer companies that actually recruit in or remotely for the candidate's area.
        Answer with company names only, one per line, no numbering, no comment, no URL.
        """;

    /** Resolution is bounded: a whole round must not hold an HTTP request. */
    private static final int RESOLUTION_TIMEOUT_SECONDS = 30;
    private static final int MAX_LIMIT = 12;

    private final ChatClient chatClient;
    private final CandidateProfileRepository profileRepository;
    private final AtsBoardResolver boardResolver;

    public AtsBoardSuggestionService(
        @Qualifier("chatClientSmall") ChatClient chatClient,
        CandidateProfileRepository profileRepository,
        AtsBoardResolver boardResolver
    ) {
        this.chatClient = chatClient;
        this.profileRepository = profileRepository;
        this.boardResolver = boardResolver;
    }

    /**
     * Returns the boards that could be resolved for the user's profile, richest first.
     *
     * @return an empty list when the user has no profile yet, or when no proposed company had a
     *         reachable public board — never a list of unverified guesses
     */
    public List<BoardSuggestionDto> suggestFor(String userId, int limit) {
        CandidateProfile profile = profileRepository.findTopByUserIdOrderByCreatedAtDesc(userId).orElse(null);
        if (profile == null) {
            log.info("Board suggestions skipped: no candidate profile for {}", userId);
            return List.of();
        }
        List<String> companies = proposeCompanies(profile, Math.clamp(limit, 1, MAX_LIMIT));
        return companies.isEmpty() ? List.of() : resolveAll(companies);
    }

    /** Asks the model for company names only — never for slugs or URLs, which it would invent. */
    private List<String> proposeCompanies(CandidateProfile profile, int limit) {
        try {
            String answer = chatClient
                .prompt()
                .user(spec ->
                    spec
                        .text(PROMPT)
                        .param("roles", StringUtils.defaultIfBlank(profile.getPreferredRoles(), "—"))
                        .param("skills", StringUtils.defaultIfBlank(profile.getSkills(), "—"))
                        .param("location", StringUtils.defaultIfBlank(profile.getLocation(), "—"))
                        .param("limit", String.valueOf(limit))
                )
                .call()
                .content();

            return StringUtils.isBlank(answer)
                ? List.of()
                : answer
                      .lines()
                      .map(line -> StringUtils.trimToEmpty(StringUtils.removePattern(line, "^\\s*[-*\\d.)]+\\s*")))
                      .filter(StringUtils::isNotEmpty)
                      .distinct()
                      .limit(limit)
                      .toList();
        } catch (Exception e) {
            log.warn("Board suggestion model call failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** Resolves the proposed companies concurrently; each one is several HTTP probes. */
    private List<BoardSuggestionDto> resolveAll(List<String> companies) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<BoardSuggestionDto>> futures = companies
                .stream()
                .map(company -> executor.submit(() -> resolveOne(company)))
                .toList();
            return futures
                .stream()
                .map(this::get)
                .filter(Objects::nonNull)
                .sorted((a, b) -> Integer.compare(b.offerCount(), a.offerCount()))
                .toList();
        }
    }

    private BoardSuggestionDto resolveOne(String company) {
        return boardResolver
            .resolve(company)
            .map(board ->
                new BoardSuggestionDto(
                    company,
                    board.source().name(),
                    board.slug(),
                    board.boardUrl(),
                    board.offers().size(),
                    board
                        .offers()
                        .stream()
                        .map(offer -> offer.getTitle())
                        .filter(StringUtils::isNotBlank)
                        .limit(3)
                        .toList()
                )
            )
            .orElse(null);
    }

    private BoardSuggestionDto get(Future<BoardSuggestionDto> future) {
        try {
            return future.get(RESOLUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
