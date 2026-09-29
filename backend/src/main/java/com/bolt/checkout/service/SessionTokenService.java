package com.bolt.checkout.service;

import com.bolt.checkout.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

/**
 * Issues a compact, signed session token after a successful OTP verification.
 *
 * <p>This is deliberately a small, self-contained application-level mechanism rather than a
 * full authentication framework: the token is an HMAC-SHA256 signed, time-limited payload that
 * lets the checkout endpoint trust the authenticated user id without trusting client input.
 * The signing secret comes from {@code SESSION_TOKEN_SECRET} and is never sent to the browser.
 */
@Service
public class SessionTokenService {

    private static final Logger log = LoggerFactory.getLogger(SessionTokenService.class);

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final long VALIDITY_MILLIS = 30L * 60L * 1000L; // 30 minutes

    /** Placeholder from application.properties; acceptable only for local development. */
    private static final String DEV_DEFAULT_SECRET = "local-dev-only-change-me-0123456789abcdef";

    private final byte[] secret;

    public SessionTokenService(@Value("${session.token.secret}") String secret, Environment environment) {
        if (DEV_DEFAULT_SECRET.equals(secret) && isProduction(environment)) {
            // Failing fast beats silently signing tokens with a public secret.
            throw new IllegalStateException(
                    "SESSION_TOKEN_SECRET must be set to a unique random value outside local development. "
                            + "Generate one with: openssl rand -base64 48");
        }
        if (DEV_DEFAULT_SECRET.equals(secret)) {
            log.warn("SESSION_TOKEN_SECRET is using the local development default. "
                    + "Set a unique value before deploying.");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    private static boolean isProduction(Environment environment) {
        return Arrays.asList(environment.getActiveProfiles()).contains("prod");
    }

    /** Builds the token returned to the client after a successful OTP verification. */
    public String issue(User user) {
        long expiresAt = System.currentTimeMillis() + VALIDITY_MILLIS;
        String payload = String.join("|",
                String.valueOf(user.getId()),
                encode(user.getFirstName()),
                encode(user.getLastName()),
                encode(user.getEmail()),
                String.valueOf(expiresAt)
        );
        String encodedPayload = base64Url(payload.getBytes(StandardCharsets.UTF_8));
        return encodedPayload + "." + base64Url(sign(encodedPayload));
    }

    /** Parses and verifies a token. Returns empty when the token is missing, tampered with or expired. */
    public Optional<AuthenticatedUser> parse(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.", 2);
        if (parts.length != 2) {
            return Optional.empty();
        }
        String encodedPayload = parts[0];
        String providedSignature = parts[1];

        byte[] expected;
        byte[] actual;
        try {
            expected = decode(providedSignature);
            actual = sign(encodedPayload);
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
        if (!MessageDigest.isEqual(expected, actual)) {
            return Optional.empty();
        }

        String[] fields;
        try {
            fields = decodePayload(encodedPayload).split("\\|", -1);
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
        if (fields.length != 5) {
            return Optional.empty();
        }

        long expiresAt;
        long userId;
        try {
            expiresAt = Long.parseLong(fields[4]);
            userId = Long.parseLong(fields[0]);
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
        if (expiresAt < System.currentTimeMillis()) {
            return Optional.empty();
        }

        return Optional.of(new AuthenticatedUser(
                userId,
                decodeField(fields[1]),
                decodeField(fields[2]),
                decodeField(fields[3])
        ));
    }

    private byte[] sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to sign session token", ex);
        }
    }

    private static String encode(String value) {
        return base64Url(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeField(String value) {
        return new String(decode(value), StandardCharsets.UTF_8);
    }

    private static String decodePayload(String value) {
        return new String(decode(value), StandardCharsets.UTF_8);
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] decode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    /** Immutable view of the authenticated principal carried by a session token. */
    public record AuthenticatedUser(Long userId, String firstName, String lastName, String email) {
    }
}
