package com.bolt.checkout.config;

import com.bolt.checkout.dto.ErrorResponse;
import com.bolt.checkout.service.RateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Per-IP rate limiting for the two endpoints an attacker would grind against:
 * {@code POST /api/auth/verify} (guessing a 6-digit code) and
 * {@code GET /api/auth/recognize} (probing which emails are registered).
 *
 * <p>The response body is built with the same {@code ErrorResponse} shape as every other
 * error, so clients only have to understand one error format.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter verifyLimiter;
    private final RateLimiter recognizeLimiter;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimiter verifyLimiter, RateLimiter recognizeLimiter, ObjectMapper objectMapper) {
        this.verifyLimiter = verifyLimiter;
        this.recognizeLimiter = recognizeLimiter;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        RateLimiter limiter = null;
        String path = request.getRequestURI();

        if ("POST".equalsIgnoreCase(request.getMethod()) && path.endsWith("/api/auth/verify")) {
            limiter = verifyLimiter;
        } else if ("GET".equalsIgnoreCase(request.getMethod()) && path.endsWith("/api/auth/recognize")) {
            limiter = recognizeLimiter;
        }

        if (limiter == null) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimiter.Decision decision = limiter.tryAcquire(clientKey(request));
        if (decision.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
        objectMapper.writeValue(response.getWriter(), new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "Too many requests. Please try again shortly.",
                path
        ));
    }

    /**
     * Honours {@code X-Forwarded-For} because the backend runs behind Vercel and Render,
     * where {@code getRemoteAddr()} is the proxy - keying on it would rate-limit every
     * visitor as a single client.
     */
    private static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }
}
