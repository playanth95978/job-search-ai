package nc.opt.aiassistant.web.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import nc.opt.aiassistant.config.ApplicationProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Test class for {@link UserRateLimitFilter}.
 */
class UserRateLimitFilterUnitTest {

    private static final String METERED_PATH = "/api/cv-builder/generate";

    @BeforeEach
    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    private static ApplicationProperties.RateLimit properties(int capacity, List<String> paths) {
        ApplicationProperties.RateLimit props = new ApplicationProperties.RateLimit();
        props.setEnabled(true);
        props.setCapacity(capacity);
        props.setRefillPeriod(Duration.ofMinutes(1));
        props.setPaths(paths);
        return props;
    }

    private static void authenticate(String login) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(login, login));
    }

    /** Drives one request through the filter and returns the response. */
    private static MockHttpServletResponse call(UserRateLimitFilter filter, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr("203.0.113.7");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void allowsRequestsUpToCapacityThenRefuses() throws Exception {
        UserRateLimitFilter filter = new UserRateLimitFilter(properties(3, List.of("/api/cv-builder/**")));
        authenticate("alice");

        for (int i = 1; i <= 3; i++) {
            assertThat(call(filter, METERED_PATH).getStatus()).as("request %d is within the quota", i).isEqualTo(200);
        }

        MockHttpServletResponse refused = call(filter, METERED_PATH);
        assertThat(refused.getStatus()).isEqualTo(429);
        assertThat(refused.getHeader("Retry-After")).isNotNull();
        assertThat(refused.getContentAsString()).contains("error.ratelimit");
    }

    @Test
    void quotaIsPerCallerNotGlobal() throws Exception {
        UserRateLimitFilter filter = new UserRateLimitFilter(properties(1, List.of("/api/cv-builder/**")));

        authenticate("alice");
        assertThat(call(filter, METERED_PATH).getStatus()).isEqualTo(200);
        assertThat(call(filter, METERED_PATH).getStatus()).isEqualTo(429);

        // Bob must not inherit Alice's exhausted bucket.
        authenticate("bob");
        assertThat(call(filter, METERED_PATH).getStatus()).isEqualTo(200);
    }

    @Test
    void doesNotMeterPathsOutsideTheList() throws Exception {
        UserRateLimitFilter filter = new UserRateLimitFilter(properties(1, List.of("/api/cv-builder/**")));
        authenticate("alice");

        // Cheap CRUD is deliberately unmetered: many calls, never refused.
        for (int i = 0; i < 5; i++) {
            assertThat(call(filter, "/api/conversations").getStatus()).isEqualTo(200);
        }
    }

    @Test
    void metersAnonymousCallersByAddress() throws Exception {
        UserRateLimitFilter filter = new UserRateLimitFilter(properties(1, List.of("/api/job-copilot/jobs/search-smart")));
        // No authentication: the caller is keyed by IP instead of login.
        assertThat(call(filter, "/api/job-copilot/jobs/search-smart").getStatus()).isEqualTo(200);
        assertThat(call(filter, "/api/job-copilot/jobs/search-smart").getStatus()).isEqualTo(429);
    }

    @Test
    void disabledFilterLetsEverythingThrough() throws Exception {
        ApplicationProperties.RateLimit props = properties(1, List.of("/api/cv-builder/**"));
        props.setEnabled(false);
        UserRateLimitFilter filter = new UserRateLimitFilter(props);
        authenticate("alice");

        for (int i = 0; i < 5; i++) {
            assertThat(call(filter, METERED_PATH).getStatus()).isEqualTo(200);
        }
    }

    /**
     * Guards the shipped defaults: a typo in a pattern would otherwise surface only at startup, and
     * an endpoint silently dropping out of the list would go unnoticed.
     */
    @Test
    void defaultPathsParseAndCoverTheExpensiveEndpoints() throws Exception {
        ApplicationProperties.RateLimit defaults = new ApplicationProperties.RateLimit();
        UserRateLimitFilter filter = new UserRateLimitFilter(properties(1, defaults.getPaths()));

        List<String> expensive = List.of(
            "/api/cv-builder/resume/pdf",
            "/api/job-copilot/assistant/abc/stream",
            "/api/job-copilot/interview/start",
            "/api/job-copilot/panel/start",
            "/api/job-copilot/career/simulate",
            "/api/job-copilot/cv/upload",
            "/api/job-copilot/opportunities/live",
            "/api/job-copilot/jobs/fetch",
            "/api/job-copilot/jobs/match-advanced",
            "/api/job-copilot/jobs/11111111-1111-1111-1111-111111111111/enrich",
            "/api/job-copilot/jobs/11111111-1111-1111-1111-111111111111/positioning",
            "/api/job-copilot/jobs/11111111-1111-1111-1111-111111111111/tailored/pdf"
        );

        for (String path : expensive) {
            authenticate("caller-" + path);
            assertThat(call(filter, path).getStatus()).as("%s consumes its single token", path).isEqualTo(200);
            assertThat(call(filter, path).getStatus()).as("%s is metered", path).isEqualTo(429);
        }
    }

    /** The public, cacheable search must stay unmetered — it is the landing page's own query. */
    @Test
    void defaultPathsLeavePublicSearchAlone() throws Exception {
        ApplicationProperties.RateLimit defaults = new ApplicationProperties.RateLimit();
        UserRateLimitFilter filter = new UserRateLimitFilter(properties(1, defaults.getPaths()));

        for (int i = 0; i < 3; i++) {
            assertThat(call(filter, "/api/job-copilot/jobs/search").getStatus()).isEqualTo(200);
            assertThat(call(filter, "/api/job-copilot/jobs/indexed-count").getStatus()).isEqualTo(200);
        }
    }
}
