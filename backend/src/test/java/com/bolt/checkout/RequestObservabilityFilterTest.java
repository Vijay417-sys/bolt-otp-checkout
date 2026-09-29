package com.bolt.checkout;

import com.bolt.checkout.config.RequestObservabilityFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** Covers the correlation id and the baseline security headers. */
class RequestObservabilityFilterTest {

    private final RequestObservabilityFilter filter = new RequestObservabilityFilter(false);

    @Test
    @DisplayName("Every response carries a request id and the baseline security headers")
    void addsHeaders() throws Exception {
        MockHttpServletResponse response = run(filter, new MockHttpServletRequest("GET", "/api/health"), null);

        assertThat(response.getHeader(RequestObservabilityFilter.REQUEST_ID_HEADER)).isNotBlank();
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
    }

    @Test
    @DisplayName("A client-supplied request id is echoed back, so a request can be traced end to end")
    void echoesAnInboundRequestId() throws Exception {
        MockHttpServletResponse response =
                run(filter, new MockHttpServletRequest("GET", "/api/health"), "trace-abc-123");

        assertThat(response.getHeader(RequestObservabilityFilter.REQUEST_ID_HEADER)).isEqualTo("trace-abc-123");
    }

    @Test
    @DisplayName("A hostile request id is replaced rather than written into the log")
    void rejectsAnUnsafeInboundRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");

        // The id ends up in a log line, so anything that could forge or corrupt one is dropped.
        assertThat(run(filter, request, "bad value\nFake-Log-Line: injected")
                .getHeader(RequestObservabilityFilter.REQUEST_ID_HEADER)).doesNotContain("injected");

        MockHttpServletRequest tooLong = new MockHttpServletRequest("GET", "/api/health");
        assertThat(run(filter, tooLong, "x".repeat(200))
                .getHeader(RequestObservabilityFilter.REQUEST_ID_HEADER)).hasSizeLessThan(64);
    }

    @Test
    @DisplayName("The request id is put in the MDC while the request is handled, and removed afterwards")
    void managesTheMdc() throws Exception {
        String[] seenInMdc = new String[1];
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
        request.addHeader(RequestObservabilityFilter.REQUEST_ID_HEADER, "mdc-check");

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) ->
                seenInMdc[0] = MDC.get(RequestObservabilityFilter.MDC_KEY));

        assertThat(seenInMdc[0]).isEqualTo("mdc-check");
        // A leaked id would be attached to the next request served by the same thread.
        assertThat(MDC.get(RequestObservabilityFilter.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("HSTS is not sent over plain HTTP, and not at all while it is disabled")
    void hstsRequiresHttpsAndTheFlag() throws Exception {
        MockHttpServletRequest plain = new MockHttpServletRequest("GET", "/api/health");
        assertThat(run(new RequestObservabilityFilter(true), plain, null)
                .getHeader("Strict-Transport-Security")).isNull();

        assertThat(run(new RequestObservabilityFilter(false), secureRequest(), null)
                .getHeader("Strict-Transport-Security")).isNull();

        assertThat(run(new RequestObservabilityFilter(true), secureRequest(), null)
                .getHeader("Strict-Transport-Security")).contains("max-age=63072000");
    }

    private static MockHttpServletRequest secureRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
        request.setSecure(true);
        return request;
    }

    private static MockHttpServletResponse run(RequestObservabilityFilter filter,
                                               MockHttpServletRequest request,
                                               String inboundRequestId) throws Exception {
        if (inboundRequestId != null) {
            request.addHeader(RequestObservabilityFilter.REQUEST_ID_HEADER, inboundRequestId);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> { });
        return response;
    }
}
