package nc.opt.aiassistant.service.ai;

import io.github.bucket4j.Bucket;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Client-side throttle in front of the Mistral chat API. The free tier answers 429 as soon as the
 * request rate goes above a couple of calls per second, and a 429 in the middle of a conversation is
 * far more expensive (lost turn, retry storm) than waiting a few hundred milliseconds here.
 *
 * <p>A single token bucket is shared by the whole application — the limit is per API key, not per
 * user. Callers either block ({@link #acquire()}, for the servlet path) or compose reactively
 * ({@link #acquireReactive()}, for the SSE path, where blocking a Netty/servlet thread is not an
 * option). When the wait would exceed {@code max-wait}, acquisition fails fast so the caller can
 * degrade (fallback model) instead of piling up.
 */
@Slf4j
@Component
public class MistralRateLimiter implements DisposableBean {

    private final Bucket bucket;
    private final ScheduledExecutorService scheduler;
    private final Duration maxWait;

    public MistralRateLimiter(
        @Value("${spring.ai.mistral.rate-limit.requests-per-second:3}") int requestsPerSecond,
        @Value("${spring.ai.mistral.rate-limit.burst-capacity:0}") int burstCapacity,
        @Value("${spring.ai.mistral.rate-limit.max-wait:20s}") Duration maxWait
    ) {
        int rate = Math.max(1, requestsPerSecond);
        // A burst larger than the sustained rate lets a conversation start immediately after an idle
        // period without spending its whole budget in the first second.
        int capacity = burstCapacity > 0 ? burstCapacity : rate;
        this.maxWait = maxWait;
        this.bucket = Bucket.builder()
            .addLimit(limit -> limit.capacity(capacity).refillGreedy(rate, Duration.ofSeconds(1)))
            .build();
        ThreadFactory factory = Thread.ofVirtual().name("mistral-rate-limiter-", 0).factory();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(factory);
        log.info("Mistral rate limiter: {} req/s (burst {}), max wait {}", rate, capacity, maxWait);
    }

    /** Blocking acquisition, for the non-reactive chat path. Returns false when the wait is too long. */
    public boolean acquire() {
        try {
            return bucket.asBlocking().tryConsume(1, maxWait.toNanos());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Reactive acquisition: completes once a token is available, or errors with
     * {@link RateLimitedException} when the wait would exceed {@code max-wait}. Nothing blocks —
     * the wait is scheduled, so no request thread is held while throttling.
     */
    public Mono<Void> acquireReactive() {
        return Mono.defer(() -> Mono.fromFuture(bucket.asScheduler().tryConsume(1, maxWait.toNanos(), scheduler)))
            .flatMap(acquired -> Boolean.TRUE.equals(acquired) ? Mono.empty() : Mono.error(new RateLimitedException()))
            .then();
    }

    /** Tokens currently available; exposed for diagnostics and tests. */
    public long availableTokens() {
        return bucket.getAvailableTokens();
    }

    @Override
    public void destroy() {
        scheduler.shutdownNow();
    }

    /** Raised when the local throttle could not grant a slot within {@code max-wait}. */
    public static class RateLimitedException extends RuntimeException {

        public RateLimitedException() {
            super("Local Mistral rate limit: no slot available in time");
        }
    }
}
