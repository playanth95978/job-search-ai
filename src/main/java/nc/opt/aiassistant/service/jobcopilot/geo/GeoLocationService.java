package nc.opt.aiassistant.service.jobcopilot.geo;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * GeoLocation agent: normalises free-text locations (CV, search box, job offers) into structured
 * {@link GeoLocation}s and scores how well two of them match, with proximity awareness
 * (same city / agglomeration / province / country / remote).
 *
 * <p>Resolution is deterministic first ({@link GeoGazetteer}); only genuinely unknown strings fall
 * back to the LLM ({@code chatClientSmall}), and every resolution is cached in-memory keyed by the
 * normalised string. Callers scoring many offers pass {@code allowLlm=false} so the LLM is hit at
 * most once per request (for the reference location), never once per offer.
 */
@Slf4j
@Service
public class GeoLocationService {

    private final ChatClient chatClient;
    private final boolean llmFallbackEnabled;

    /** Resolution cache keyed by normalised raw string; warms across requests. */
    private final ConcurrentHashMap<String, GeoLocation> cache = new ConcurrentHashMap<>();

    public GeoLocationService(
        @Qualifier("chatClientSmall") ChatClient chatClient,
        @Value("${application.ai.geo.llm-fallback:true}") boolean llmFallbackEnabled
    ) {
        this.chatClient = chatClient;
        this.llmFallbackEnabled = llmFallbackEnabled;
    }

    /** Deterministic-only resolution (never calls the LLM). Reads the cache if warm. */
    public GeoLocation resolve(String raw) {
        return resolve(raw, false);
    }

    /**
     * Resolves a location. When {@code allowLlm} is true and the gazetteer can't place the string,
     * the LLM is consulted (once, then cached). Failures degrade gracefully to a deterministic result.
     */
    public GeoLocation resolve(String raw, boolean allowLlm) {
        String key = GeoGazetteer.normalize(raw);
        if (key.isBlank()) return GeoLocation.unknown(raw);

        GeoLocation cached = cache.get(key);
        if (cached != null) return cached;

        GeoLocation deterministic = GeoGazetteer.parse(raw);
        if (deterministic.known() || !allowLlm || !llmFallbackEnabled) {
            // Only cache confident deterministic hits; keep retrying unknowns until the LLM can help.
            if (deterministic.known()) cache.put(key, deterministic);
            return deterministic;
        }

        GeoLocation resolved = resolveWithLlm(raw, deterministic);
        cache.put(key, resolved);
        return resolved;
    }

    /**
     * Scores how relevant {@code offer} is to the {@code reference} (0..1). Remote offers stay
     * broadly relevant; a confident different-country pair is a mismatch; unknown offers sit low-neutral.
     */
    public GeoScore score(GeoLocation reference, GeoLocation offer) {
        if (reference == null || !reference.usable()) {
            return new GeoScore(0.5, GeoMatchLevel.UNKNOWN); // no usable reference -> don't penalise
        }
        if (reference.remote() && offer.remote()) {
            return new GeoScore(1.0, GeoMatchLevel.REMOTE);
        }
        if (offer.remote()) {
            return new GeoScore(0.85, GeoMatchLevel.REMOTE); // remote works from anywhere
        }
        if (!offer.known()) {
            return new GeoScore(0.4, GeoMatchLevel.UNKNOWN);
        }
        if (reference.hasCity() && offer.hasCity() && reference.city().equals(offer.city())) {
            return new GeoScore(1.0, GeoMatchLevel.EXACT);
        }
        if (
            reference.hasCity() &&
            offer.hasCity() &&
            GeoGazetteer.isGrandNoumea(reference.city()) &&
            GeoGazetteer.isGrandNoumea(offer.city())
        ) {
            return new GeoScore(0.9, GeoMatchLevel.AGGLOMERATION);
        }
        if (reference.region() != null && reference.region().equals(offer.region())) {
            return new GeoScore(0.7, GeoMatchLevel.REGION);
        }
        if (reference.hasCountry() && reference.countryCode().equals(offer.countryCode())) {
            return new GeoScore(0.5, GeoMatchLevel.COUNTRY);
        }
        return new GeoScore(0.15, GeoMatchLevel.MISMATCH);
    }

    /** Best score of an offer against any of several reference locations (CV + preferred locations). */
    public GeoScore bestScore(Collection<GeoLocation> references, GeoLocation offer) {
        return references
            .stream()
            .map(ref -> score(ref, offer))
            .max((a, b) -> Double.compare(a.score(), b.score()))
            .orElseGet(() -> new GeoScore(0.5, GeoMatchLevel.UNKNOWN));
    }

    private GeoLocation resolveWithLlm(String raw, GeoLocation fallback) {
        try {
            String prompt = """
                Resolve this job location string into its geographic components.
                Set remote=true if it denotes remote / anywhere / télétravail.
                Use a short country code such as NC (New Caledonia), FR or AU.
                Location string: "%s"
                """.formatted(raw);
            // Spring AI's BeanOutputConverter appends the JSON schema and maps the reply for us.
            GeoResolution r = chatClient.prompt().user(prompt).call().entity(GeoResolution.class);
            if (r == null) {
                return fallback;
            }
            boolean remote = Boolean.TRUE.equals(r.remote());
            String city = normLower(blankToNull(r.city()));
            String countryCode = blankToNull(r.countryCode());
            boolean known = city != null || countryCode != null;
            if (!known && !remote) {
                return fallback;
            }
            return new GeoLocation(
                raw,
                city,
                blankToNull(r.region()),
                blankToNull(r.country()),
                countryCode == null ? null : countryCode.toUpperCase(),
                remote,
                known
            );
        } catch (Exception e) {
            log.debug("Geo LLM fallback failed for '{}': {}", raw, e.getMessage());
            return fallback;
        }
    }

    private static String normLower(String city) {
        return city == null ? null : GeoGazetteer.normalize(city);
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() || "null".equalsIgnoreCase(t) ? null : t;
    }

    /** Sample of recognised local locations, for tool descriptions / prompts. */
    public List<String> knownCitySamples() {
        return GeoGazetteer.knownCitySamples();
    }
}
