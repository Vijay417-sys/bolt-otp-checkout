package com.bolt.checkout.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Adds a correlation id to every request and the baseline set of security headers.
 *
 * <p>The id is put in the SLF4J {@link MDC} under {@code requestId}, which is the key the
 * production log pattern already prints, so every line logged while handling a request
 * carries it. A client-supplied {@code X-Request-Id} is honoured, so a request can be
 * traced across the Vercel -> Render hop; anything unparseable is replaced rather than
 * trusted, since the value is written into logs.
 *
 * <p>{@code spring-boot-starter-security} is deliberately not on the classpath, so the
 * usual header filter is not available. These are set explicitly instead. HSTS is only
 * sent over HTTPS - sending it over plain HTTP is meaningless and would let a downgrade
 * strip it - so it is conditional on the request already being secure.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestObservabilityFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    /** Guards against a client stuffing a huge or malformed value into the log line. */
    private static final int MAX_INBOUND_ID_LENGTH = 64;

    private final boolean hstsEnabled;

    public RequestObservabilityFilter(@Value("${security.hsts-enabled:false}") boolean hstsEnabled) {
        this.hstsEnabled = hstsEnabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        MDC.put(MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        applySecurityHeaders(request, response);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Must be cleared in a finally block: a leaked id would be attached to the
            // next request handled by the same thread.
            MDC.remove(MDC_KEY);
        }
    }

    private void applySecurityHeaders(HttpServletRequest request, HttpServletResponse response) {
        // Stops a browser from re-interpreting a response as a different content type.
        response.setHeader("X-Content-Type-Options", "nosniff");
        // Blocks framing, so the app cannot be embedded in a clickjacking page.
        response.setHeader("X-Frame-Options", "DENY");
        // Do not leak the full URL, including any query string, to third parties.
        response.setHeader("Referrer-Policy", "no-referrer");

        if (hstsEnabled && request.isSecure()) {
            // Two years, subdomains included, preload-eligible.
            response.setHeader("Strict-Transport-Security", "max-age=63072000; includeSubDomains; preload");
        }
    }

    private static String resolveRequestId(HttpServletRequest request) {
        String inbound = request.getHeader(REQUEST_ID_HEADER);
        if (inbound != null && !inbound.isBlank() && inbound.length() <= MAX_INBOUND_ID_LENGTH) {
            // Accept only characters that cannot corrupt a log line.
            if (inbound.matches("[A-Za-z0-9._-]+")) {
                return inbound;
            }
        }
        return UUID.randomUUID().toString();
    }
}
