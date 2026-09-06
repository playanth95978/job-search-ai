package nc.opt.aiassistant.service.ai;

import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

/**
 * {@link ChatModel} decorator that spends one {@link MistralRateLimiter} token per provider call.
 *
 * <p>The throttle used to be applied by hand, in the single service that remembered to do it. Every
 * other Mistral caller — le panel d'ombre, la lettre de motivation, la réécriture de CV, le parcours
 * de carrière, l'OCR — went straight to the API, and the panel alone fires one call per juré in a
 * tight loop. Decorating the model itself makes the limit unavoidable: a service cannot opt out by
 * forgetting, and a future one is covered the day it is written.
 *
 * <p>Wrapping the model rather than each {@code ChatClient} also keeps the budget honest — the token
 * is spent where the HTTP request actually leaves, so retries and tool-call round trips each count.
 */
public class ThrottledChatModel implements ChatModel {

    private final ChatModel delegate;
    private final MistralRateLimiter rateLimiter;

    public ThrottledChatModel(ChatModel delegate, MistralRateLimiter rateLimiter) {
        this.delegate = delegate;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        if (!rateLimiter.acquire()) {
            throw new MistralRateLimiter.RateLimitedException();
        }
        return delegate.call(prompt);
    }

    /**
     * Streaming path: the wait is scheduled rather than blocking, so no request thread is parked
     * while the bucket refills. {@code Flux.defer} keeps the acquisition per-subscription — a flux
     * that is retried or replayed pays for itself again.
     */
    @Override
    public @NonNull Flux<ChatResponse> stream(@NonNull Prompt prompt) {
        return rateLimiter.acquireReactive().thenMany(Flux.defer(() -> delegate.stream(prompt)));
    }

    @Override
    public @NonNull ChatOptions getOptions() {
        return delegate.getOptions();
    }

    /** The undecorated model, for the rare caller that legitimately needs to bypass the budget. */
    public ChatModel delegate() {
        return delegate;
    }
}
