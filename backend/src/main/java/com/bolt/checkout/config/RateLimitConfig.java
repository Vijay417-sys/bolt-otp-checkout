package com.bolt.checkout.config;

import com.bolt.checkout.service.RateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the per-IP rate limiter.
 *
 * <p>Separate limiters per endpoint because they defend against different things: a tight
 * limit on {@code /verify} (someone grinding a 6-digit code) and a looser one on
 * {@code /recognize} (someone enumerating registered emails), which the checkout form calls
 * on every debounced keystroke and so must tolerate a burst from a single user.
 *
 * <p>A limit of {@code 0} or less disables that limiter, which is how the test profile
 * avoids having unrelated assertions trip over a shared counter.
 */
@Configuration
public class RateLimitConfig {

    @Value("${rate-limit.verify-per-minute:10}")
    private int verifyPerMinute;

    @Value("${rate-limit.recognize-per-minute:60}")
    private int recognizePerMinute;

    @Bean
    public RateLimiter verifyRateLimiter() {
        return new RateLimiter(verifyPerMinute);
    }

    @Bean
    public RateLimiter recognizeRateLimiter() {
        return new RateLimiter(recognizePerMinute);
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(RateLimiter verifyRateLimiter,
                                                                  RateLimiter recognizeRateLimiter,
                                                                  ObjectMapper objectMapper) {
        FilterRegistrationBean<RateLimitFilter> registration =
                new FilterRegistrationBean<>(new RateLimitFilter(
                        verifyRateLimiter, recognizeRateLimiter, objectMapper));
        registration.addUrlPatterns("/api/auth/verify", "/api/auth/recognize");
        registration.setOrder(1);
        return registration;
    }
}
