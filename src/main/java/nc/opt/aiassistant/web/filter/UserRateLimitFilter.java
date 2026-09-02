package nc.opt.aiassistant.web.filter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import nc.opt.aiassistant.config.ApplicationProperties;
import nc.opt.aiassistant.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Per-caller quota on the expensive endpoints — those that run an LLM turn, an embedding, a rerank,
 * an outbound scrape or a PDF layout.
 *
 * <p>This is deliberately distinct from {@code MistralRateLimiter}, which is a single
 * application-wide bucket sized to the provider's API key: it stops us from being throttled by
 * Mistral, but it does nothing to stop one account from consuming the whole budget. Without a
 * per-caller limit, a single {@code ROLE_USER} looping on {@code /api/cv-builder/generate} or the
 * assistant stream drives unbounded paid inference and starves every other user.</p>
 *
 * <p>Keyed by login when the caller is authenticated, and by client IP otherwise, so the handful of
 * {@code permitAll} endpoints matched by {@code paths} are covered too. IP keying is only as good as
 * {@code server.forward-headers-strategy}: behind the production proxy that property must be set,
 * or every anonymous caller collapses onto the proxy's own address.</p>
 *
 * <p>Refusal is immediate — a 429 with {@code Retry-After} — never a wait. Blocking here would hold
 * a request thread and turn a flood into an outage, which is the opposite of the point.</p>
 */
public class UserRateLimitFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(UserRateLimitFilter.class);

    /** Bounds memory: IP keys are attacker-chosen, so the map must not grow with them. */
    private static final long MAX_TRACKED_CALLERS = 50_000;

    private final boolean enabled;
    private final int capacity;
    private final Duration refillPeriod;
    private final List<PathPattern> patterns;
    private final Cache<String, Bucket> buckets;

    public UserRateLimitFilter(ApplicationProperties.RateLimit properties) {
        this.enabled = properties.isEnabled();
        this.capacity = Math.max(1, properties.getCapacity());
        this.refillPeriod = properties.getRefillPeriod();
        PathPatternParser parser = PathPatternParser.defaultInstance;
        this.patterns = properties.getPaths().stream().map(parser::parse).toList();
        // expireAfterAccess, not expireAfterWrite: an idle caller is forgotten, but a caller who
        // keeps hitting the limit keeps the same (empty) bucket instead of getting a fresh one.
        this.buckets = Caffeine.newBuilder()
            .maximumSize(MAX_TRACKED_CALLERS)
            .expireAfterAccess(refillPeriod.toMillis() * 2, TimeUnit.MILLISECONDS)
            .build();
        if (enabled) {
            LOG.info("Per-caller rate limit: {} requests / {} on {} path pattern(s)", capacity, refillPeriod, patterns.size());
        } else {
            LOG.warn("Per-caller rate limit is DISABLED (application.rate-limit.enabled=false)");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || !isRateLimited(request);
    }

    private boolean isRateLimited(HttpServletRequest request) {
        // Request URI includes the contextPath if any, removed it.
        String path = request.getRequestURI().substring(request.getContextPath().length());
        PathContainer container = PathContainer.parsePath(path);
        return patterns.stream().anyMatch(pattern -> pattern.matches(container));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        String key = callerKey(request);
        ConsumptionProbe probe = buckets.get(key, k -> newBucket()).tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            filterChain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds());
        LOG.warn("Rate limit hit by {} on {} — refusing for {}s", key, request.getRequestURI(), retryAfterSeconds);
        reject(response, retryAfterSeconds);
    }

    private Bucket newBucket() {
        return Bucket.builder()
            .addLimit(limit -> limit.capacity(capacity).refillGreedy(capacity, refillPeriod))
            .build();
    }

    /**
     * Login when authenticated, client address otherwise. The {@code user:} / {@code ip:} prefixes
     * keep the two namespaces from colliding — a login that looks like an address must not share a
     * bucket with that address.
     */
    private String callerKey(HttpServletRequest request) {
        return SecurityUtils.getCurrentUserLogin()
            .map(login -> "user:" + login)
            .orElseGet(() -> "ip:" + request.getRemoteAddr());
    }

    private void reject(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response
            .getWriter()
            .write(
                "{\"type\":\"https://www.jhipster.tech/problem/problem-with-message\"," +
                    "\"title\":\"Too Many Requests\",\"status\":429," +
                    "\"detail\":\"Rate limit exceeded, retry in " +
                    retryAfterSeconds +
                    "s\"," +
                    "\"message\":\"error.ratelimit\"}"
            );
    }
}
