package nc.opt.aiassistant.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.time.Duration;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

/**
 * Shared HTTP plumbing for the job-board connectors.
 *
 * <p>Each connector used to call {@code RestClient.create()} on every request: a brand-new client
 * (and connection pool) per call, with the platform's default timeouts — meaning <em>no</em> read
 * timeout, so one unresponsive job board could hang an ingestion step indefinitely. A single shared,
 * timeout-bounded client removes both problems.</p>
 */
@Configuration
public class AtsHttpConfiguration {

    /** Job boards are third parties: fail fast rather than hold an ingestion thread. */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    /** Several boards reject the default Java user agent outright. */
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; JobSearchAI/1.0)";

    @Bean("atsRestClient")
    public RestClient atsRestClient(RestClient.Builder builder) {
        HttpClientSettings settings = HttpClientSettings.defaults().withConnectTimeout(CONNECT_TIMEOUT).withReadTimeout(READ_TIMEOUT);
        return builder
            .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
            .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
            .build();
    }

    /**
     * Mapper dedicated to third-party payloads. Unknown properties are ignored on purpose: a job
     * board adding a field must never break ingestion, and the connectors' records only declare what
     * they actually map. Kept separate from the application mapper, whose strictness is a feature.
     */
    @Bean("atsObjectMapper")
    public ObjectMapper atsObjectMapper() {
        return JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();
    }
}
