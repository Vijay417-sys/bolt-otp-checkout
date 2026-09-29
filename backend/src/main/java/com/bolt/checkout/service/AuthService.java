package com.bolt.checkout.service;

import com.bolt.checkout.dto.*;
import com.bolt.checkout.entity.User;
import com.bolt.checkout.exception.DuplicateEmailException;
import com.bolt.checkout.exception.InvalidOtpException;
import com.bolt.checkout.exception.OtpExpiredException;
import com.bolt.checkout.exception.OtpLockedException;
import com.bolt.checkout.exception.UserNotFoundException;
import com.bolt.checkout.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Business logic for registration, email recognition and OTP verification.
 *
 * <p>Only the BCrypt hash of the login code is ever persisted. The plain code is returned to the
 * caller only from {@link #register(RegistrationRequest)} because the assignment requires it to be
 * displayed on screen, and from the successful-login response because the code is rotated on every
 * login.
 *
 * <p>Brute-force resistance comes from three independent controls, because a 6-digit code has only
 * a million possible values and a single BCrypt check is not enough on its own:
 * expiry, per-account attempt limiting, and per-IP rate limiting (see {@code RateLimitFilter}).
 */
@Service
@Transactional
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int OTP_BOUND = 1_000_000;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionTokenService sessionTokenService;
    private final AuditService auditService;
    private final OtpAttemptService otpAttemptService;
    private final SecureRandom secureRandom = new SecureRandom();

    private final long otpTtlMinutes;
    private final int otpMaxAttempts;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       SessionTokenService sessionTokenService,
                       AuditService auditService,
                       OtpAttemptService otpAttemptService,
                       @Value("${otp.ttl-minutes:10}") long otpTtlMinutes,
                       @Value("${otp.max-attempts:5}") int otpMaxAttempts) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionTokenService = sessionTokenService;
        this.auditService = auditService;
        this.otpAttemptService = otpAttemptService;
        this.otpTtlMinutes = otpTtlMinutes;
        this.otpMaxAttempts = otpMaxAttempts;
    }

    public RegistrationResponse register(RegistrationRequest request) {
        String email = normalizeEmail(request.getEmail());

        if (userRepository.findByEmail(email).isPresent()) {
            auditService.record(AuditService.EVENT_REGISTRATION, AuditService.OUTCOME_FAILURE,
                    email, null, "duplicate email");
            throw new DuplicateEmailException("Email is already registered");
        }

        String otp = generateOtp();

        User user = new User();
        user.setEmail(email);
        user.setFirstName(request.getFirstName().trim());
        user.setLastName(request.getLastName().trim());
        user.setOtpHash(passwordEncoder.encode(otp));
        user.setOtpExpiresAt(LocalDateTime.now().plusMinutes(otpTtlMinutes));
        user.setOtpFailedAttempts(0);
        user.setOtpLockedUntil(null);
        userRepository.save(user);

        // Intentionally not logged: the plain OTP must never reach the logs.
        log.info("Registered user id={} email={}", user.getId(), email);
        auditService.record(AuditService.EVENT_REGISTRATION, AuditService.OUTCOME_SUCCESS,
                email, user.getId(), null);

        return new RegistrationResponse("Registration successful", otp);
    }

    public RecognitionResponse recognize(String email) {
        return new RecognitionResponse(userRepository.findByEmail(normalizeEmail(email)).isPresent());
    }

    public LoginResponse verifyOtp(VerifyOtpRequest request) {
        String email = normalizeEmail(request.getEmail());
        LocalDateTime now = LocalDateTime.now();

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            auditService.record(AuditService.EVENT_OTP_VERIFY, AuditService.OUTCOME_FAILURE,
                    email, null, "unknown email");
            throw new UserNotFoundException("No account found for this email");
        }

        // Lockout is checked before expiry and before the code itself: a locked account must
        // not confirm anything about the code the caller supplied.
        if (user.isOtpLocked(now)) {
            long retryAfter = Duration.between(now, user.getOtpLockedUntil()).toSeconds();
            auditService.record(AuditService.EVENT_OTP_VERIFY, AuditService.OUTCOME_FAILURE,
                    email, user.getId(), "account locked");
            throw new OtpLockedException(
                    "Too many incorrect codes. Try again later.", retryAfter);
        }

        if (user.isOtpExpired(now)) {
            auditService.record(AuditService.EVENT_OTP_VERIFY, AuditService.OUTCOME_FAILURE,
                    email, user.getId(), "code expired");
            throw new OtpExpiredException("Login code has expired. Please register again.");
        }

        if (!passwordEncoder.matches(request.getCode(), user.getOtpHash())) {
            registerFailedAttempt(user, email);
            throw new InvalidOtpException("Invalid login code");
        }

        // Success: clear the failure state and rotate the code so a captured one cannot be reused.
        user.setOtpFailedAttempts(0);
        user.setOtpLockedUntil(null);
        String rotatedCode = generateOtp();
        user.setOtpHash(passwordEncoder.encode(rotatedCode));
        user.setOtpExpiresAt(now.plusMinutes(otpTtlMinutes));
        userRepository.save(user);

        auditService.record(AuditService.EVENT_OTP_VERIFY, AuditService.OUTCOME_SUCCESS,
                email, user.getId(), null);
        log.info("Verified login user id={} email={}", user.getId(), email);

        return new LoginResponse(
                true,
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                sessionTokenService.issue(user),
                rotatedCode
        );
    }

    /**
     * Counts a wrong code and locks the account once the limit is reached.
     *
     * <p>Delegated to {@link OtpAttemptService} rather than written here, because this
     * method's caller is about to throw: a write on this transaction would be rolled back
     * with the failed request and the counter would never rise.
     *
     * <p>The attempt that trips the limit still reports "invalid code" rather than
     * "locked"; announcing the lock immediately would confirm to an attacker that their
     * guesses were being counted. The lock surfaces as 429 on the following attempt.
     */
    private void registerFailedAttempt(User user, String email) {
        int attempts = otpAttemptService.recordFailure(user.getId());

        if (attempts >= otpMaxAttempts) {
            log.warn("Locked OTP for user id={} after {} failed attempts", user.getId(), attempts);
        }

        auditService.record(AuditService.EVENT_OTP_VERIFY, AuditService.OUTCOME_FAILURE,
                email, user.getId(), "incorrect code, attempt " + attempts);
    }

    /** Generates a cryptographically strong 6-digit numeric code, always zero-padded. */
    private String generateOtp() {
        return String.format("%06d", secureRandom.nextInt(OTP_BOUND));
    }

    /** Canonical email form used consistently across registration, recognition, verification and checkout. */
    public static String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        return email.trim().toLowerCase();
    }
}
