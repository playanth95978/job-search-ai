package nc.opt.aiassistant.service.ai.voxtral;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The Mistral API key, read from the same property Spring AI's Mistral chat model uses.
 *
 * <p>Deliberately not duplicated under {@code application.ai.voxtral}: one key, one place to rotate it.
 * Wrapped in a bean rather than injected as a raw String so it is obvious at every call site that a
 * secret is being handled, and so it never lands in an {@code ApplicationProperties} {@code toString()}.
 */
@Component
public record VoxtralApiKey(@Value("${spring.ai.mistralai.api-key:}") String value) {
    public boolean isPresent() {
        return value != null && !value.isBlank();
    }
}
