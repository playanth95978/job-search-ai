package nc.opt.aiassistant.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

class ThrottledChatModelTest {

    /** Compte les appels réellement transmis au fournisseur — c'est ce que le seau doit borner. */
    private static final class CountingChatModel implements ChatModel {

        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public ChatResponse call(Prompt prompt) {
            calls.incrementAndGet();
            return new ChatResponse(List.of(new Generation(new AssistantMessage("ok"))));
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.defer(() -> {
                calls.incrementAndGet();
                return Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage("ok")))));
            });
        }
    }

    private static MistralRateLimiter limiter(int perSecond, int burst, Duration maxWait) {
        return new MistralRateLimiter(perSecond, burst, maxWait);
    }

    @Test
    void spendsOneTokenPerBlockingCall() {
        CountingChatModel delegate = new CountingChatModel();
        MistralRateLimiter limiter = limiter(1, 3, Duration.ofSeconds(1));
        ThrottledChatModel model = new ThrottledChatModel(delegate, limiter);

        long before = limiter.availableTokens();
        model.call(new Prompt("hi"));

        assertThat(delegate.calls).hasValue(1);
        assertThat(limiter.availableTokens()).isEqualTo(before - 1);
    }

    @Test
    void refusesTheCallRatherThanQueueingForeverWhenTheBucketIsEmpty() {
        CountingChatModel delegate = new CountingChatModel();
        // Un seul jeton, et une attente si courte (1 ms pour un refill de 1/s) que le second appel
        // ne peut pas l'obtenir. Bucket4j refuse une attente nulle, d'où la milliseconde.
        ThrottledChatModel model = new ThrottledChatModel(delegate, limiter(1, 1, Duration.ofMillis(1)));

        model.call(new Prompt("first"));

        assertThatThrownBy(() -> model.call(new Prompt("second"))).isInstanceOf(MistralRateLimiter.RateLimitedException.class);
        // Le fournisseur n'a jamais vu le second appel : c'est tout l'intérêt, ne pas le facturer.
        assertThat(delegate.calls).hasValue(1);
    }

    @Test
    void streamingAlsoGoesThroughTheBucket() {
        CountingChatModel delegate = new CountingChatModel();
        MistralRateLimiter limiter = limiter(1, 2, Duration.ofSeconds(1));
        ThrottledChatModel model = new ThrottledChatModel(delegate, limiter);

        long before = limiter.availableTokens();
        assertThat(model.stream(new Prompt("hi")).collectList().block()).hasSize(1);

        assertThat(delegate.calls).hasValue(1);
        assertThat(limiter.availableTokens()).isEqualTo(before - 1);
    }

    @Test
    void streamingErrorsInsteadOfCallingTheProviderWhenTheBucketIsEmpty() {
        CountingChatModel delegate = new CountingChatModel();
        ThrottledChatModel model = new ThrottledChatModel(delegate, limiter(1, 1, Duration.ofMillis(1)));

        assertThat(model.stream(new Prompt("first")).collectList().block()).hasSize(1);

        assertThatThrownBy(() -> model.stream(new Prompt("second")).collectList().block()).isInstanceOf(
            MistralRateLimiter.RateLimitedException.class
        );

        assertThat(delegate.calls).hasValue(1);
    }
}
