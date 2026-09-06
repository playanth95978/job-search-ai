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

    /**
     * Le throttle local n'est pas un 429 du fournisseur. Il l'était pour ce classifieur tant que
     * l'acquisition se faisait hors du modèle, dans l'agent, qui s'en servait pour dégrader vers le
     * petit modèle. Maintenant que {@link ThrottledChatModel} tient le seau en amont, un rejeu sur
     * le repli repasserait par le même seau : il attendrait `max-wait` une seconde fois pour échouer
     * à l'identique. Le message porte pourtant « rate limit », d'où ce test de non-régression.
     */
    @Test
    void ignoresLocalThrottleRejection() {
        assertThat(AiErrors.isRateLimited(new MistralRateLimiter.RateLimitedException())).isFalse();
        assertThat(AiErrors.isRateLimited(new RuntimeException("wrapped", new MistralRateLimiter.RateLimitedException()))).isFalse();
    }

    @Test
    void ignoresUnrelatedFailures() {
        assertThat(AiErrors.isRateLimited(new HttpClientErrorException(HttpStatus.BAD_REQUEST))).isFalse();
        assertThat(AiErrors.isRateLimited(new IllegalStateException("boom"))).isFalse();
        assertThat(AiErrors.isRateLimited(null)).isFalse();
    }
}
