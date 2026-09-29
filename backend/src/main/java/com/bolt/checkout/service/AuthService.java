package com.bolt.checkout.service;

import com.bolt.checkout.dto.*;
import com.bolt.checkout.entity.User;
import com.bolt.checkout.exception.DuplicateEmailException;
import com.bolt.checkout.exception.InvalidOtpException;
import com.bolt.checkout.exception.UserNotFoundException;
import com.bolt.checkout.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;

/**
 * Business logic for registration, email recognition and OTP verification.
 *
 * <p>Only the BCrypt hash of the login code is ever persisted. The plain code is returned to the
 * caller only from {@link #register(RegistrationRequest)} because the assignment requires it to be
 * displayed on screen.
 */
@Service
@Transactional
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int OTP_BOUND = 1_000_000;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionTokenService sessionTokenService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       SessionTokenService sessionTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionTokenService = sessionTokenService;
    }

    public RegistrationResponse register(RegistrationRequest request) {
        String email = normalizeEmail(request.getEmail());

        if (userRepository.findByEmail(email).isPresent()) {
            throw new DuplicateEmailException("Email is already registered");
        }

        String otp = generateOtp();

        User user = new User();
        user.setEmail(email);
        user.setFirstName(request.getFirstName().trim());
        user.setLastName(request.getLastName().trim());
        user.setOtpHash(passwordEncoder.encode(otp));
        userRepository.save(user);

        // Intentionally not logged: the plain OTP must never reach the logs.
        log.info("Registered user id={} email={}", user.getId(), email);

        return new RegistrationResponse("Registration successful", otp);
    }

    public RecognitionResponse recognize(String email) {
        return new RecognitionResponse(userRepository.findByEmail(normalizeEmail(email)).isPresent());
    }

    public LoginResponse verifyOtp(VerifyOtpRequest request) {
        String email = normalizeEmail(request.getEmail());
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("No account found for this email"));

        if (!passwordEncoder.matches(request.getCode(), user.getOtpHash())) {
            throw new InvalidOtpException("Invalid login code");
        }

        return new LoginResponse(
                true,
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                sessionTokenService.issue(user)
        );
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
