package nc.opt.aiassistant.service.ai;

import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/** Helpers to classify errors coming back from the LLM providers. */
public final class AiErrors {

    private AiErrors() {}

    /**
     * Tells whether a failure is a provider-side rate limit (HTTP 429). Spring AI wraps the original
     * exception several layers deep (retry / reactive operators), and depending on the transport the
     * status can surface as a {@link WebClientResponseException}, a {@link HttpStatusCodeException} or
     * only in the message — so the whole cause chain is inspected, with a textual fallback.
     */
    public static boolean isRateLimited(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (
                t instanceof WebClientResponseException.TooManyRequests ||
                (t instanceof HttpStatusCodeException e && e.getStatusCode().value() == 429)
            ) {
                return true;
            }
            if (mentionsRateLimit(t.getMessage())) {
                return true;
            }
        }
        return false;
    }

    private static boolean mentionsRateLimit(String message) {
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase();
        return (
            lower.contains("429") ||
            lower.contains("too many requests") ||
            lower.contains("rate limit") ||
            lower.contains("service tier capacity exceeded")
        );
    }
}
