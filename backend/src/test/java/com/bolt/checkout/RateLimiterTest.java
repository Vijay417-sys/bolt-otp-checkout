package com.bolt.checkout;

import com.bolt.checkout.service.RateLimiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit coverage for the fixed-window limiter, with no Spring context. */
class RateLimiterTest {

    @Test
    @DisplayName("Requests up to the limit are allowed and the next one is refused")
    void allowsUpToTheLimitThenRefuses() {
        RateLimiter limiter = new RateLimiter(3);

        assertThat(limiter.tryAcquire("1.2.3.4").allowed()).isTrue();
        assertThat(limiter.tryAcquire("1.2.3.4").allowed()).isTrue();
        assertThat(limiter.tryAcquire("1.2.3.4").allowed()).isTrue();

        RateLimiter.Decision refused = limiter.tryAcquire("1.2.3.4");
        assertThat(refused.allowed()).isFalse();
        assertThat(refused.retryAfterSeconds()).isPositive();
    }

    @Test
    @DisplayName("Each client is counted separately")
    void countersAreIsolatedPerClient() {
        RateLimiter limiter = new RateLimiter(1);

        assertThat(limiter.tryAcquire("1.1.1.1").allowed()).isTrue();
        assertThat(limiter.tryAcquire("1.1.1.1").allowed()).isFalse();

        // A different address still has its full budget.
        assertThat(limiter.tryAcquire("2.2.2.2").allowed()).isTrue();
    }

    @Test
    @DisplayName("A limit of zero disables the limiter")
    void zeroDisablesTheLimiter() {
        RateLimiter limiter = new RateLimiter(0);

        for (int i = 0; i < 500; i++) {
            assertThat(limiter.tryAcquire("1.2.3.4").allowed()).isTrue();
        }
    }

    @Test
    @DisplayName("reset clears the counters")
    void resetClearsCounters() {
        RateLimiter limiter = new RateLimiter(1);
        limiter.tryAcquire("1.2.3.4");
        assertThat(limiter.tryAcquire("1.2.3.4").allowed()).isFalse();

        limiter.reset();

        assertThat(limiter.tryAcquire("1.2.3.4").allowed()).isTrue();
    }

    @Test
    @DisplayName("The limit is exposed so the configuration can be asserted")
    void exposesItsLimit() {
        assertThat(new RateLimiter(7).getLimit()).isEqualTo(7);
    }
}
