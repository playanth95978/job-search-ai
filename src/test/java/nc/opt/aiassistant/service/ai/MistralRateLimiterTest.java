package nc.opt.aiassistant.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class MistralRateLimiterTest {

    @Test
    void grantsTheConfiguredBurstImmediately() {
        MistralRateLimiter limiter = new MistralRateLimiter(1, 2, Duration.ofMillis(50));
        try {
            assertThat(limiter.acquire()).isTrue();
            assertThat(limiter.acquire()).isTrue();
        } finally {
            limiter.destroy();
        }
    }

    @Test
    void refusesWhenTheWaitExceedsMaxWait() {
        // 1 req/s, no burst: the second call would have to wait ~1s, far beyond max-wait.
        MistralRateLimiter limiter = new MistralRateLimiter(1, 1, Duration.ofMillis(20));
        try {
            assertThat(limiter.acquire()).isTrue();
            assertThat(limiter.acquire()).isFalse();
        } finally {
            limiter.destroy();
        }
    }

    @Test
    void reactiveAcquisitionErrorsWithRateLimitedExceptionWhenSaturated() {
        MistralRateLimiter limiter = new MistralRateLimiter(1, 1, Duration.ofMillis(20));
        try {
            limiter.acquireReactive().block(Duration.ofSeconds(5));
            assertThatThrownBy(() -> limiter.acquireReactive().block(Duration.ofSeconds(5))).isInstanceOf(
                MistralRateLimiter.RateLimitedException.class
            );
        } finally {
            limiter.destroy();
        }
    }

    @Test
    void reactiveAcquisitionSucceedsWhenTokensRefillWithinMaxWait() {
        MistralRateLimiter limiter = new MistralRateLimiter(10, 1, Duration.ofSeconds(2));
        try {
            assertThatCode(() -> {
                limiter.acquireReactive().block(Duration.ofSeconds(5));
                limiter.acquireReactive().block(Duration.ofSeconds(5));
            }).doesNotThrowAnyException();
        } finally {
            limiter.destroy();
        }
    }
}
