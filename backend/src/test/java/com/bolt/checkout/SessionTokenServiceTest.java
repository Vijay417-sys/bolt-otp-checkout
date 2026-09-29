package com.bolt.checkout;

import com.bolt.checkout.entity.User;
import com.bolt.checkout.service.SessionTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SessionTokenServiceTest extends IntegrationTestBase {

    @Autowired
    private SessionTokenService sessionTokenService;

    private User user;

    @BeforeEach
    void setUpUser() {
        user = new User();
        user.setId(42L);
        user.setEmail("vijay@example.com");
        user.setFirstName("Vijay");
        user.setLastName("Hosapeti");
    }

    @Test
    @DisplayName("A freshly issued token round-trips to the authenticated user")
    void issuedTokenRoundTrips() {
        String token = sessionTokenService.issue(user);

        Optional<SessionTokenService.AuthenticatedUser> parsed = sessionTokenService.parse(token);

        assertThat(parsed).isPresent();
        assertThat(parsed.get().userId()).isEqualTo(42L);
        assertThat(parsed.get().firstName()).isEqualTo("Vijay");
        assertThat(parsed.get().lastName()).isEqualTo("Hosapeti");
        assertThat(parsed.get().email()).isEqualTo("vijay@example.com");
    }

    @Test
    @DisplayName("A tampered payload is rejected")
    void tamperedPayloadRejected() {
        String token = sessionTokenService.issue(user);
        String[] parts = token.split("\\.", 2);
        String tampered = parts[0].substring(0, parts[0].length() - 2) + "AA." + parts[1];

        assertThat(sessionTokenService.parse(tampered)).isEmpty();
    }

    @Test
    @DisplayName("A tampered signature is rejected")
    void tamperedSignatureRejected() {
        String token = sessionTokenService.issue(user);
        String payload = token.split("\\.", 2)[0];

        assertThat(sessionTokenService.parse(payload + ".AAAAAAAAAAAAAAAAAAAAAAAAAAAA")).isEmpty();
    }

    @Test
    @DisplayName("Missing, blank and malformed tokens are rejected without error")
    void malformedTokensRejected() {
        assertThat(sessionTokenService.parse(null)).isEmpty();
        assertThat(sessionTokenService.parse("")).isEmpty();
        assertThat(sessionTokenService.parse("   ")).isEmpty();
        assertThat(sessionTokenService.parse("no-dot")).isEmpty();
        assertThat(sessionTokenService.parse("!!!.???")).isEmpty();
    }

    @Test
    @DisplayName("The token does not contain the user id in plain readable form")
    void tokenIsOpaque() {
        String token = sessionTokenService.issue(user);

        assertThat(token).doesNotContain("vijay@example.com");
        assertThat(token.split("\\.")).hasSize(2);
    }
}