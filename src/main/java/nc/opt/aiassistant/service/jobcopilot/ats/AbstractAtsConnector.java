package nc.opt.aiassistant.service.jobcopilot.ats;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

/**
 * Common ground for every job-board connector: the shared HTTP client, JSON decoding into records,
 * and the handful of normalisations each source needs (HTML stripping, remote detection, stable
 * identity, CSV configuration).
 *
 * <p>Sealed: the set of connectors is a closed, curated list — adding a source is a deliberate act
 * that belongs in this file, and the compiler now says so. Subclasses stay {@code final}.</p>
 *
 * <p>Every network failure is swallowed and logged here rather than propagated: connectors are
 * queried in a loop by {@code JobSearchService} and in Spring Batch steps, where one unavailable
 * board must degrade to "no results" instead of failing the whole search or run.</p>
 */
@Slf4j
public abstract sealed class AbstractAtsConnector
    implements AtsConnector
    permits
        AdzunaConnector,
        AshbyConnector,
        CareerjetConnector,
        EmploiNcConnector,
        FranceTravailConnector,
        GreenhouseConnector,
        HelloWorkConnector,
        JobicyConnector,
        LeverConnector,
        RecruiteeConnector,
        RemoteOkConnector,
        SeekConnector,
        SmartRecruitersConnector,
        WorkableConnector
{

    /** Words that mark an offer as remote, in the languages the sources publish in. */
    private static final List<String> REMOTE_MARKERS = List.of("remote", "télétravail", "teletravail", "work from home", "anywhere");

    protected final RestClient restClient;
    protected final ObjectMapper objectMapper;

    protected AbstractAtsConnector(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    // --- HTTP ---------------------------------------------------------------------------------

    /**
     * GETs {@code uri} and decodes the body into {@code type}.
     *
     * @return the decoded payload, or {@code null} if the call or the decoding failed (already logged)
     */
    protected <T> T getJson(String uri, Class<T> type) {
        return getJson(uri, type, request -> {});
    }

    /** Same, with per-request headers (auth, referer…) applied by {@code customizer}. */
    protected <T> T getJson(String uri, Class<T> type, Consumer<RestClient.RequestHeadersSpec<?>> customizer) {
        try {
            RestClient.RequestHeadersSpec<?> spec = restClient.get().uri(URI.create(uri));
            customizer.accept(spec);
            String body = spec.retrieve().body(String.class);
            return body == null ? null : objectMapper.readValue(body, type);
        } catch (Exception e) {
            log.error("{} fetch failed for {}: {}", getSource(), uri, e.getMessage());
            return null;
        }
    }

    /** POSTs {@code payload} as JSON and decodes the response into {@code type}. */
    protected <T> T postJson(String uri, Object payload, Class<T> type) {
        try {
            String body = restClient.post().uri(URI.create(uri)).body(payload).retrieve().body(String.class);
            return body == null ? null : objectMapper.readValue(body, type);
        } catch (Exception e) {
            log.error("{} post failed for {}: {}", getSource(), uri, e.getMessage());
            return null;
        }
    }

    /** HTTP Basic header value; an empty password is what key-only APIs expect. */
    protected static String basicAuth(String user, String password) {
        String token = Base64.getEncoder().encodeToString(
            (user + ":" + StringUtils.defaultString(password)).getBytes(StandardCharsets.UTF_8)
        );
        return "Basic " + token;
    }

    protected static Consumer<RestClient.RequestHeadersSpec<?>> header(String name, String value) {
        return spec -> spec.header(name, value);
    }

    protected static Consumer<RestClient.RequestHeadersSpec<?>> headers(String... nameValuePairs) {
        return spec -> {
            for (int i = 0; i + 1 < nameValuePairs.length; i += 2) {
                spec.header(nameValuePairs[i], nameValuePairs[i + 1]);
            }
        };
    }

    protected static final String REFERER = HttpHeaders.REFERER;
    protected static final String AUTHORIZATION = HttpHeaders.AUTHORIZATION;

    // --- Normalisation ------------------------------------------------------------------------

    /** Turns an HTML description into plain text; blank in, blank out. */
    protected static String stripHtml(String html) {
        return StringUtils.isBlank(html) ? "" : Jsoup.parse(html).text();
    }

    /**
     * Heuristic remote detection for sources with no boolean flag: looks for the usual markers in the
     * title, location and description. Sources that <em>do</em> expose a flag must use it instead.
     */
    protected static boolean detectRemote(String... fields) {
        String haystack = StringUtils.join(fields, ' ').toLowerCase(Locale.ROOT);
        return REMOTE_MARKERS.stream().anyMatch(haystack::contains);
    }

    /**
     * Client-side keyword filtering for feed sources that offer no query parameter.
     *
     * <p><b>Tous</b> les mots doivent apparaître, et la recherche porte sur l'ensemble des champs
     * fournis — pas seulement le titre. Chercher la requête entière comme une sous-chaîne d'un seul
     * champ ne remonte rien dès que l'utilisateur saisit deux mots ("java spring"), puisqu'aucun
     * intitulé ne contient littéralement cette suite.</p>
     */
    protected static boolean matchesAllKeywords(String keywords, String... fields) {
        if (StringUtils.isBlank(keywords)) {
            return true;
        }
        String haystack = StringUtils.lowerCase(StringUtils.join(fields, ' '));
        return Arrays.stream(StringUtils.split(StringUtils.lowerCase(keywords))).allMatch(haystack::contains);
    }

    /**
     * Stable identity for sources that expose no id — or an unstable one, such as a signed redirect
     * that changes on every call. Hashing the posting's invariants keeps deduplication working across
     * ingestion runs, since it keys on (source, sourceId).
     */
    protected static String stableId(String... parts) {
        return StringUtils.substring(DigestUtils.sha256Hex(StringUtils.join(parts, '|')), 0, 32);
    }

    /** Splits a comma-separated configuration value (board slugs, seed queries…). */
    protected static List<String> csv(String value) {
        return StringUtils.isBlank(value)
            ? List.of()
            : Arrays.stream(StringUtils.split(value, ',')).map(StringUtils::trimToEmpty).filter(StringUtils::isNotEmpty).toList();
    }

    /** Path segment right after {@code marker}, query string removed (board/company slug in a URL). */
    protected static String slugAfter(String url, String marker) {
        return StringUtils.substringBefore(AtsConnector.boardSlug(url, marker), "?");
    }
}
