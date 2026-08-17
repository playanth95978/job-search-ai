package nc.opt.aiassistant.service.jobcopilot.ats;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.JobOffer;
import nc.opt.aiassistant.domain.enumeration.JobSource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * Finds which ATS hosts a company's job board, from a company <em>name</em> — no URL to paste.
 *
 * <p>Board-based connectors (Greenhouse, Lever, SmartRecruiters, Ashby, Workable, Recruitee) each
 * need a slug, and nothing in a company name says which ATS it uses. Rather than asking the user for
 * a URL, or asking a model to guess a slug it would happily hallucinate, the resolver derives a few
 * deterministic slug candidates and <b>probes the real APIs</b>: a candidate is accepted only when a
 * board actually answers with postings. The proof is the offers themselves.</p>
 *
 * <p>Probes for one candidate run concurrently on virtual threads — six HTTP calls that each fail
 * fast — and the first ATS to answer, in connector declaration order, wins.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AtsBoardResolver {

    /** Beyond this, we are guessing rather than normalising. */
    private static final int MAX_SLUG_CANDIDATES = 4;

    /** A whole probe round is bounded: a silent board must not hold a user request. */
    private static final int PROBE_TIMEOUT_SECONDS = 20;

    private final List<AtsConnector> connectors;

    /** Public board URL per ATS, rebuilt from a slug — what the user sees and can open. */
    private static final java.util.Map<JobSource, String> BOARD_URL_TEMPLATE = java.util.Map.of(
        JobSource.GREENHOUSE,
        "https://boards.greenhouse.io/%s",
        JobSource.LEVER,
        "https://jobs.lever.co/%s",
        JobSource.SMARTRECRUITERS,
        "https://jobs.smartrecruiters.com/%s",
        JobSource.ASHBY,
        "https://jobs.ashbyhq.com/%s",
        JobSource.WORKABLE,
        "https://apply.workable.com/%s",
        JobSource.RECRUITEE,
        "https://%s.recruitee.com"
    );

    /** A company board that was actually reached, with the postings that proved it. */
    public record ResolvedBoard(JobSource source, String slug, List<JobOffer> offers) {
        /** Public page of the board; also the URL the existing fetch-by-URL endpoint accepts. */
        public String boardUrl() {
            String template = BOARD_URL_TEMPLATE.get(source);
            return template == null ? slug : template.formatted(slug);
        }
    }

    /**
     * Resolves a company board from a name ("Hugging Face") or from any board URL — the URL path
     * stays supported so a pasted link keeps working.
     *
     * @return the board that answered, or empty if no ATS recognised any candidate slug
     */
    public Optional<ResolvedBoard> resolve(String companyOrUrl) {
        String input = StringUtils.trimToEmpty(companyOrUrl);
        if (input.isEmpty()) {
            return Optional.empty();
        }
        if (StringUtils.containsAny(input, "://", "www.")) {
            return resolveFromUrl(input);
        }
        for (String slug : slugCandidates(input)) {
            Optional<ResolvedBoard> found = probe(slug);
            if (found.isPresent()) {
                log.info(
                    "Resolved company '{}' to {} board '{}' ({} offers)",
                    input,
                    found.get().source(),
                    slug,
                    found.get().offers().size()
                );
                return found;
            }
        }
        log.info("No ATS board found for company '{}'", input);
        return Optional.empty();
    }

    private Optional<ResolvedBoard> resolveFromUrl(String url) {
        return connectors
            .stream()
            .filter(connector -> connector.supports(url))
            .map(connector -> new ResolvedBoard(connector.getSource(), url, connector.fetchFromUrl(url)))
            .filter(board -> !board.offers().isEmpty())
            .findFirst();
    }

    /**
     * Asks every board connector about one slug, in parallel, and keeps the first non-empty answer in
     * connector order — so the outcome does not depend on which HTTP call happens to return first.
     */
    private Optional<ResolvedBoard> probe(String slug) {
        List<AtsConnector> candidates = connectors.stream().filter(this::isBoardBased).toList();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<List<JobOffer>>> futures = candidates
                .stream()
                .map(c -> executor.submit(() -> c.fetchBySlug(slug)))
                .toList();
            for (int i = 0; i < candidates.size(); i++) {
                List<JobOffer> offers = get(futures.get(i));
                if (!offers.isEmpty()) {
                    return Optional.of(new ResolvedBoard(candidates.get(i).getSource(), slug, offers));
                }
            }
        }
        return Optional.empty();
    }

    private List<JobOffer> get(Future<List<JobOffer>> future) {
        try {
            return future.get(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (Exception e) {
            // A board that errors is simply "not this ATS": connectors already log the cause.
            return List.of();
        }
    }

    /** Board-based connectors are exactly those that implement the slug lookup. */
    private boolean isBoardBased(AtsConnector connector) {
        try {
            return !connector.getClass().getMethod("fetchBySlug", String.class).isDefault();
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /**
     * Deterministic slug candidates for a company name, most likely first: accents stripped, then the
     * two conventions ATS boards actually use — {@code huggingface} and {@code hugging-face} — plus
     * the raw input in case the user already typed the slug.
     */
    List<String> slugCandidates(String companyName) {
        String normalised = StringUtils.stripAccents(StringUtils.lowerCase(StringUtils.trim(companyName)));
        String compact = normalised.replaceAll("[^a-z0-9]", "");
        String hyphenated = normalised.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");

        Set<String> candidates = new LinkedHashSet<>();
        addIfUsable(candidates, compact);
        addIfUsable(candidates, hyphenated);
        addIfUsable(candidates, normalised);
        // "ACME Inc." and "ACME SAS" are boarded as "acme" far more often than with their legal suffix.
        addIfUsable(candidates, StringUtils.removePattern(compact, "(inc|sas|sarl|ltd|llc|gmbh|group)$"));
        return new ArrayList<>(candidates).stream().sorted(Comparator.comparingInt(String::length)).limit(MAX_SLUG_CANDIDATES).toList();
    }

    private void addIfUsable(Set<String> candidates, String candidate) {
        if (StringUtils.length(candidate) >= 2) {
            candidates.add(candidate);
        }
    }
}
