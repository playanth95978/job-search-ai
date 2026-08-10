package nc.opt.aiassistant.service.jobcopilot;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.config.ApplicationProperties;
import org.springframework.stereotype.Component;

/**
 * Checks whether a stored offer apply URL is still reachable. Deliberately conservative: only a hard
 * {@code 404}/{@code 410} counts as {@link Liveness#DEAD}. Any other status, a bot-block (403/429),
 * a server error or a network/timeout error is {@link Liveness#UNKNOWN} — never treated as dead — so
 * a live offer is never purged because a board rate-limited or blocked us. Tries {@code HEAD} first
 * and falls back to {@code GET} when the server rejects the method.
 */
@Slf4j
@Component
public class OfferLivenessChecker {

    public enum Liveness {
        ALIVE,
        DEAD,
        UNKNOWN,
    }

    private static final String USER_AGENT = "Mozilla/5.0 (compatible; JobSearchAiBot/1.0; +https://aicarreerfinder.com)";

    private final HttpClient httpClient;
    private final Duration timeout;

    public OfferLivenessChecker(ApplicationProperties applicationProperties) {
        int timeoutSeconds = applicationProperties.getJobCopilot().getLiveness().getHttpTimeoutSeconds();
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.httpClient = HttpClient.newBuilder().connectTimeout(this.timeout).followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    public Liveness check(String url) {
        if (url == null || url.isBlank()) return Liveness.UNKNOWN;
        URI uri;
        try {
            uri = URI.create(url.trim());
            if (uri.getHost() == null) return Liveness.UNKNOWN;
        } catch (Exception e) {
            return Liveness.UNKNOWN;
        }

        Liveness head = statusToLiveness(request(uri, "HEAD"));
        // Some servers reject HEAD (405/501) or block it; a GET may still reveal a real 404/410.
        if (head == Liveness.UNKNOWN) {
            return statusToLiveness(request(uri, "GET"));
        }
        return head;
    }

    /** @return the HTTP status code, or -1 on any network/timeout error. */
    private int request(URI uri, String method) {
        try {
            HttpRequest req = HttpRequest.newBuilder(uri)
                .method(method, HttpRequest.BodyPublishers.noBody())
                .timeout(timeout)
                .header("User-Agent", USER_AGENT)
                .build();
            HttpResponse<Void> resp = httpClient.send(req, HttpResponse.BodyHandlers.discarding());
            return resp.statusCode();
        } catch (Exception e) {
            log.debug("Liveness {} {} failed: {}", method, uri, e.getMessage());
            return -1;
        }
    }

    private Liveness statusToLiveness(int status) {
        if (status == 404 || status == 410) return Liveness.DEAD;
        if (status >= 200 && status < 400) return Liveness.ALIVE;
        // 403/405/429/5xx/-1 → inconclusive: don't purge on ambiguity.
        return Liveness.UNKNOWN;
    }
}
