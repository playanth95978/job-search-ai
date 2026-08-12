package nc.opt.aiassistant.service.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

class AiErrorsTest {

    @Test
    void detectsWebClientTooManyRequests() {
        Throwable error = WebClientResponseException.create(429, "Too Many Requests", null, null, null);
        assertThat(AiErrors.isRateLimited(error)).isTrue();
    }

    @Test
    void detectsRestClientTooManyRequests() {
        assertThat(AiErrors.isRateLimited(new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS))).isTrue();
    }

    @Test
    void detectsRateLimitDeepInTheCauseChain() {
        Throwable root = new IllegalStateException("429 - service tier capacity exceeded");
        assertThat(AiErrors.isRateLimited(new RuntimeException("wrapped", new RuntimeException("outer", root)))).isTrue();
    }

    @Test
    void detectsLocalThrottleRejection() {
        assertThat(AiErrors.isRateLimited(new MistralRateLimiter.RateLimitedException())).isTrue();
    }

    @Test
    void ignoresUnrelatedFailures() {
        assertThat(AiErrors.isRateLimited(new HttpClientErrorException(HttpStatus.BAD_REQUEST))).isFalse();
        assertThat(AiErrors.isRateLimited(new IllegalStateException("boom"))).isFalse();
        assertThat(AiErrors.isRateLimited(null)).isFalse();
    }
}
