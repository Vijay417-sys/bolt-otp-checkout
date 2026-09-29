package com.bolt.checkout;

import com.bolt.checkout.config.RateLimitFilter;
import com.bolt.checkout.service.RateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the filter itself: which paths it guards, what it returns when the
 * budget is spent, and that it keys on the forwarded client address.
 *
 * <p>Built by hand rather than through the Spring context, because the test profile
 * disables the limiters ({@code rate-limit.*=0}) to keep the shared counters from
 * interfering with unrelated assertions.
 */
class RateLimitFilterTest {

    // findAndRegisterModules() so ErrorResponse's LocalDateTime serialises the same way
    // Spring Boot's auto-configured mapper does.
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    @DisplayName("Requests past the limit get 429 with a Retry-After header and the standard error shape")
    void refusesPastTheLimit() throws Exception {
        RateLimitFilter filter = filterWith(1, 10);

        MockHttpServletResponse first = perform(filter, "POST", "/api/auth/verify", "1.1.1.1");
        assertThat(first.getStatus()).isEqualTo(200);

        MockHttpServletResponse second = perform(filter, "POST", "/api/auth/verify", "1.1.1.1");
        assertThat(second.getStatus()).isEqualTo(429);
        assertThat(second.getHeader("Retry-After")).isNotBlank();

        var body = objectMapper.readTree(second.getContentAsString());
        assertThat(body.get("status").asInt()).isEqualTo(429);
        assertThat(body.get("path").asText()).isEqualTo("/api/auth/verify");
        assertThat(body.get("message").asText()).isNotBlank();
    }

    @Test
    @DisplayName("The verify budget is not spent by recognition calls and vice versa")
    void endpointsHaveSeparateBudgets() throws Exception {
        RateLimitFilter filter = filterWith(1, 1);

        assertThat(perform(filter, "POST", "/api/auth/verify", "1.1.1.1").getStatus()).isEqualTo(200);
        assertThat(perform(filter, "POST", "/api/auth/verify", "1.1.1.1").getStatus()).isEqualTo(429);

        // Recognition still has its own untouched allowance.
        assertThat(perform(filter, "GET", "/api/auth/recognize", "1.1.1.1").getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("A different forwarded client gets its own budget")
    void keysOnTheForwardedClientAddress() throws Exception {
        RateLimitFilter filter = filterWith(1, 10);

        assertThat(perform(filter, "POST", "/api/auth/verify", "1.1.1.1").getStatus()).isEqualTo(200);
        assertThat(perform(filter, "POST", "/api/auth/verify", "1.1.1.1").getStatus()).isEqualTo(429);

        // Behind Vercel/Render the socket address is the proxy, so keying on it would
        // rate-limit every visitor as one client.
        assertThat(perform(filter, "POST", "/api/auth/verify", "2.2.2.2").getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Unrelated endpoints are never rate limited")
    void passesThroughUnrelatedPaths() throws Exception {
        RateLimitFilter filter = filterWith(1, 1);

        assertThat(perform(filter, "GET", "/api/health", "1.1.1.1").getStatus()).isEqualTo(200);
        assertThat(perform(filter, "POST", "/api/checkout", "1.1.1.1").getStatus()).isEqualTo(200);
        assertThat(perform(filter, "POST", "/api/auth/register", "1.1.1.1").getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("A GET to /verify is not guarded, because the limiter is method-specific")
    void ignoresTheWrongMethodOnAGuardedPath() throws Exception {
        RateLimitFilter filter = filterWith(0, 0);

        // Both limiters are disabled, so this only proves the method/path pair is matched
        // rather than the path alone.
        assertThat(perform(filter, "GET", "/api/auth/verify", "1.1.1.1").getStatus()).isEqualTo(200);
    }

    private RateLimitFilter filterWith(int verifyLimit, int recognizeLimit) {
        return new RateLimitFilter(
                new RateLimiter(verifyLimit),
                new RateLimiter(recognizeLimit),
                objectMapper);
    }

    private MockHttpServletResponse perform(RateLimitFilter filter, String method, String path, String forwardedFor)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRequestURI(path);
        request.setRemoteAddr("10.0.0.1");
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }

        MockHttpServletResponse response = new MockHttpServletResponse();
        // A chain that records whether the request was allowed through.
        FilterChain chain = new FilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                response.setStatus(200);
            }
        };

        filter.doFilter(request, response, chain);
        return response;
    }
}
