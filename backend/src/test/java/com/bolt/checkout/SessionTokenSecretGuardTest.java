package com.bolt.checkout;

import com.bolt.checkout.service.SessionTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Guards against shipping the local development signing secret to production.
 *
 * <p>No {@code @TestPropertySource} here on purpose: the secret under test is
 * supplied directly to the constructor, and adding a property source would
 * create a second Spring context. That matters because the test profile runs
 * {@code database/schema.sql} on startup, and MySQL has no
 * {@code CREATE INDEX IF NOT EXISTS}, so a second run would fail.
 */
@SpringBootTest
@ActiveProfiles("test")
class SessionTokenSecretGuardTest {

    private static final String DEV_DEFAULT = "local-dev-only-change-me-0123456789abcdef";

    @Autowired
    private SessionTokenService serviceUnderTest;

    @Test
    @DisplayName("A configured secret produces a working token service")
    void configuredSecretIsAccepted() {
        assertThat(serviceUnderTest).isNotNull();
    }

    @Test
    @DisplayName("The development default is rejected when the prod profile is active")
    void devDefaultRejectedInProduction() {
        MockEnvironment prodEnvironment = new MockEnvironment();
        prodEnvironment.setActiveProfiles("prod");

        assertThatThrownBy(() -> new SessionTokenService(DEV_DEFAULT, prodEnvironment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SESSION_TOKEN_SECRET must be set");
    }

    @Test
    @DisplayName("The development default is tolerated outside production")
    void devDefaultAllowedInDevelopment() {
        MockEnvironment devEnvironment = new MockEnvironment();
        devEnvironment.setActiveProfiles("dev");

        assertThat(new SessionTokenService(DEV_DEFAULT, devEnvironment)).isNotNull();
    }

    @Test
    @DisplayName("A real secret is accepted in production")
    void realSecretAcceptedInProduction() {
        MockEnvironment prodEnvironment = new MockEnvironment();
        prodEnvironment.setActiveProfiles("prod");

        assertThat(new SessionTokenService("a-properly-random-secret-value", prodEnvironment)).isNotNull();
    }
}
