package com.bolt.checkout;

import com.bolt.checkout.dto.RegistrationRequest;
import com.bolt.checkout.dto.VerifyOtpRequest;
import com.bolt.checkout.entity.AuditLog;
import com.bolt.checkout.entity.User;
import com.bolt.checkout.exception.InvalidOtpException;
import com.bolt.checkout.exception.OtpExpiredException;
import com.bolt.checkout.exception.OtpLockedException;
import com.bolt.checkout.exception.UserNotFoundException;
import com.bolt.checkout.repository.AuditLogRepository;
import com.bolt.checkout.service.AuthService;
import com.bolt.checkout.service.AuditService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the brute-force controls added around the login code: expiry, attempt
 * limiting with lockout, and rotation on a successful login.
 */
class OtpSecurityTest extends IntegrationTestBase {

    @Autowired
    private AuthService authService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // --------------------------------------------------------------- expiry
    @Test
    @DisplayName("An expired code is rejected even when the code itself is correct")
    void expiredCodeRejected() {
        seedExpiredUser("vijay@example.com", passwordEncoder.encode("482193"));

        assertThatThrownBy(() -> authService.verifyOtp(verify("vijay@example.com", "482193")))
                .isInstanceOf(OtpExpiredException.class)
                .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("Expiry is decided before the hash comparison, so a wrong code on an expired account reads as expired")
    void expiryIsCheckedBeforeTheCode() {
        seedExpiredUser("vijay@example.com", passwordEncoder.encode("482193"));

        assertThatThrownBy(() -> authService.verifyOtp(verify("vijay@example.com", "000000")))
                .isInstanceOf(OtpExpiredException.class);
    }

    @Test
    @DisplayName("A freshly registered code is inside its window")
    void freshRegistrationIsNotExpired() {
        authService.register(register("vijay@example.com"));

        User stored = userRepository.findByEmail("vijay@example.com").orElseThrow();
        assertThat(stored.getOtpExpiresAt()).isAfter(LocalDateTime.now());
    }

    // ------------------------------------------------------------ lockout
    @Test
    @DisplayName("Repeated wrong codes lock the account and the correct code is then refused")
    void repeatedWrongCodesLockTheAccount() {
        User user = seedUser("vijay@example.com", "Vijay", "Hosapeti", passwordEncoder.encode("482193"));

        // Five wrong guesses: each one still reports "invalid", none of them reveals the lock.
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThatThrownBy(() -> authService.verifyOtp(verify("vijay@example.com", "000000")))
                    .isInstanceOf(InvalidOtpException.class);
        }

        User locked = reload(user);
        assertThat(locked.getOtpFailedAttempts()).isEqualTo(5);
        assertThat(locked.getOtpLockedUntil()).isAfter(LocalDateTime.now());

        // The right code is now refused too, and the client is told when to come back.
        assertThatThrownBy(() -> authService.verifyOtp(verify("vijay@example.com", "482193")))
                .isInstanceOf(OtpLockedException.class)
                .satisfies(ex -> assertThat(((OtpLockedException) ex).getRetryAfterSeconds()).isPositive());
    }

    @Test
    @DisplayName("A lockout that has passed no longer blocks the correct code")
    void lockoutExpires() {
        User user = seedUser("vijay@example.com", "Vijay", "Hosapeti", passwordEncoder.encode("482193"));
        User locked = reload(user);
        locked.setOtpFailedAttempts(5);
        locked.setOtpLockedUntil(LocalDateTime.now().minusMinutes(1));
        userRepository.saveAndFlush(locked);

        assertThat(authService.verifyOtp(verify("vijay@example.com", "482193")).isSuccess()).isTrue();
    }

    @Test
    @DisplayName("A successful login clears the failure counter")
    void successResetsFailureCounter() {
        User user = seedUser("vijay@example.com", "Vijay", "Hosapeti", passwordEncoder.encode("482193"));

        assertThatThrownBy(() -> authService.verifyOtp(verify("vijay@example.com", "000000")))
                .isInstanceOf(InvalidOtpException.class);
        assertThatThrownBy(() -> authService.verifyOtp(verify("vijay@example.com", "111111")))
                .isInstanceOf(InvalidOtpException.class);
        assertThat(reload(user).getOtpFailedAttempts()).isEqualTo(2);

        authService.verifyOtp(verify("vijay@example.com", "482193"));

        assertThat(reload(user).getOtpFailedAttempts()).isZero();
        assertThat(reload(user).getOtpLockedUntil()).isNull();
    }

    // ------------------------------------------------------------ rotation
    @Test
    @DisplayName("A successful login rotates the code, so a captured one cannot be replayed")
    void codeIsRotatedOnLogin() {
        String original = authService.register(register("vijay@example.com")).getCode();

        var login = authService.verifyOtp(verify("vijay@example.com", original));
        String rotated = login.getNextCode();
        assertThat(rotated).isNotNull().matches("\\d{6}").isNotEqualTo(original);

        // Replaying the code that leaked from the registration screen no longer works.
        assertThatThrownBy(() -> authService.verifyOtp(verify("vijay@example.com", original)))
                .isInstanceOf(InvalidOtpException.class);

        // ...but the replacement code does, which is what keeps a second login possible.
        assertThat(authService.verifyOtp(verify("vijay@example.com", rotated)).isSuccess()).isTrue();
    }

    @Test
    @DisplayName("The rotated code is stored only as a hash")
    void rotatedCodeIsHashed() {
        String original = authService.register(register("vijay@example.com")).getCode();
        String rotated = authService.verifyOtp(verify("vijay@example.com", original)).getNextCode();

        User stored = userRepository.findByEmail("vijay@example.com").orElseThrow();
        assertThat(stored.getOtpHash()).isNotEqualTo(rotated);
        assertThat(passwordEncoder.matches(rotated, stored.getOtpHash())).isTrue();
    }

    // -------------------------------------------------------------- audit
    @Test
    @DisplayName("Registration and verification are recorded in the audit trail")
    void securityEventsAreAudited() {
        String code = authService.register(register("vijay@example.com")).getCode();
        authService.verifyOtp(verify("vijay@example.com", code));

        List<AuditLog> entries = auditLogRepository.findAll();

        assertThat(entries).extracting(AuditLog::getEvent)
                .contains(AuditService.EVENT_REGISTRATION, AuditService.EVENT_OTP_VERIFY);
        assertThat(entries).extracting(AuditLog::getOutcome)
                .containsOnly(AuditService.OUTCOME_SUCCESS);
    }

    @Test
    @DisplayName("Failed verifications are audited with the attempt number")
    void failedVerificationsAreAudited() {
        authService.register(register("vijay@example.com"));

        assertThatThrownBy(() -> authService.verifyOtp(verify("vijay@example.com", "000000")))
                .isInstanceOf(InvalidOtpException.class);

        assertThat(auditLogRepository.findAll())
                .filteredOn(entry -> AuditService.EVENT_OTP_VERIFY.equals(entry.getEvent()))
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.getOutcome()).isEqualTo(AuditService.OUTCOME_FAILURE);
                    assertThat(entry.getDetail()).contains("attempt 1");
                });
    }

    @Test
    @DisplayName("The audit trail never contains a login code")
    void auditTrailNeverContainsTheCode() {
        String code = authService.register(register("vijay@example.com")).getCode();
        authService.verifyOtp(verify("vijay@example.com", code));

        assertThat(auditLogRepository.findAll())
                .allSatisfy(entry -> {
                    assertThat(entry.getEvent()).doesNotContain(code);
                    assertThat(entry.getUserId() == null ? "" : String.valueOf(entry.getUserId()))
                            .doesNotContain(code);
                    // detail is null on success, so only check it when it is present.
                    if (entry.getDetail() != null) {
                        assertThat(entry.getDetail()).doesNotContain(code);
                    }
                });
    }

    @Test
    @DisplayName("A verification for an unknown email is audited before it fails")
    void unknownEmailVerificationIsAudited() {
        assertThatThrownBy(() -> authService.verifyOtp(verify("ghost@example.com", "482193")))
                .isInstanceOf(UserNotFoundException.class);

        assertThat(auditLogRepository.findAll())
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.getEvent()).isEqualTo(AuditService.EVENT_OTP_VERIFY);
                    assertThat(entry.getOutcome()).isEqualTo(AuditService.OUTCOME_FAILURE);
                });
    }

    private RegistrationRequest register(String email) {
        RegistrationRequest request = new RegistrationRequest();
        request.setEmail(email);
        request.setFirstName("Vijay");
        request.setLastName("Hosapeti");
        return request;
    }

    private VerifyOtpRequest verify(String email, String code) {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail(email);
        request.setCode(code);
        return request;
    }
}
