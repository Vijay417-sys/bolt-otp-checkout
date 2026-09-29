package com.bolt.checkout;

import com.bolt.checkout.dto.LoginResponse;
import com.bolt.checkout.dto.RegistrationRequest;
import com.bolt.checkout.dto.VerifyOtpRequest;
import com.bolt.checkout.entity.User;
import com.bolt.checkout.exception.DuplicateEmailException;
import com.bolt.checkout.exception.InvalidOtpException;
import com.bolt.checkout.exception.UserNotFoundException;
import com.bolt.checkout.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthServiceTest extends IntegrationTestBase {

    @Autowired
    private AuthService authService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ---------------------------------------------------------------- 1
    @Test
    @DisplayName("Generated codes are always exactly 6 numeric digits over many draws")
    void generatedCodesAreAlwaysSixDigits() {
        IntStream.range(0, 2000).forEach(i -> {
            var response = authService.register(request("digits" + i + "@example.com"));
            assertThat(response.getCode())
                    .as("code generated on iteration %s", i)
                    .hasSize(6)
                    .matches("\\d{6}");
        });
    }

    // ---------------------------------------------------------------- 2
    @Test
    @DisplayName("Codes are not predictable from consecutive registrations")
    void codesDifferBetweenRegistrations() {
        var first = authService.register(request("seq1@example.com")).getCode();
        var second = authService.register(request("seq2@example.com")).getCode();

        assertThat(first).isNotEqualTo(second);
    }

    // ---------------------------------------------------------------- 3
    @Test
    @DisplayName("Registration stores a BCrypt hash that matches the issued code")
    void storedHashMatchesIssuedCode() {
        var response = authService.register(request("hash@example.com"));

        var stored = userRepository.findByEmail("hash@example.com").orElseThrow();
        assertThat(stored.getOtpHash()).isNotEqualTo(response.getCode());
        assertThat(passwordEncoder.matches(response.getCode(), stored.getOtpHash())).isTrue();
    }

    // ---------------------------------------------------------------- 4
    @Test
    @DisplayName("BCrypt hashes of the same code are salted and therefore different")
    void hashesAreSalted() {
        seedUser("a@example.com", "A", "A", passwordEncoder.encode("482193"));
        seedUser("b@example.com", "B", "B", passwordEncoder.encode("482193"));

        String hashA = userRepository.findByEmail("a@example.com").orElseThrow().getOtpHash();
        String hashB = userRepository.findByEmail("b@example.com").orElseThrow().getOtpHash();

        assertThat(hashA).isNotEqualTo(hashB);
    }

    // ---------------------------------------------------------------- 5
    @Test
    @DisplayName("Duplicate registration is rejected case-insensitively")
    void duplicateRegistrationRejectedCaseInsensitively() {
        authService.register(request("vijay@example.com"));

        assertThatThrownBy(() -> authService.register(request("VIJAY@Example.COM")))
                .isInstanceOf(DuplicateEmailException.class)
                .hasMessage("Email is already registered");
    }

    // ---------------------------------------------------------------- 6
    @Test
    @DisplayName("Email normalization is applied on registration, recognition and verification")
    void emailNormalizationIsConsistent() {
        String registeredCode = authService.register(request("  Vijay@Example.COM  ")).getCode();

        assertThat(authService.recognize("VIJAY@example.com").isRegistered()).isTrue();
        assertThat(authService.recognize("  vijay@EXAMPLE.com ").isRegistered()).isTrue();

        LoginResponse login = authService.verifyOtp(verify("VIJAY@EXAMPLE.com", registeredCode));
        assertThat(login.isSuccess()).isTrue();
    }

    // ---------------------------------------------------------------- 7
    @DisplayName("Verification succeeds regardless of the email casing used")
    void verificationIsCaseInsensitive() {
        var registered = authService.register(request("vijay@example.com"));

        LoginResponse response = authService.verifyOtp(verify("VIJAY@Example.com", registered.getCode()));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getFirstName()).isEqualTo("Vijay");
    }

    // ---------------------------------------------------------------- 8
    @Test
    @DisplayName("Wrong code throws InvalidOtpException and unknown email throws UserNotFoundException")
    void verificationFailuresAreTyped() {
        authService.register(request("vijay@example.com"));

        assertThatThrownBy(() -> authService.verifyOtp(verify("vijay@example.com", "000000")))
                .isInstanceOf(InvalidOtpException.class);
        assertThatThrownBy(() -> authService.verifyOtp(verify("ghost@example.com", "482193")))
                .isInstanceOf(UserNotFoundException.class);
    }

    // ---------------------------------------------------------------- 9
    @Test
    @DisplayName("A successful verification issues a session token bound to that user")
    void successfulVerificationIssuesSessionToken() {
        var registered = authService.register(request("token@example.com"));
        User user = userRepository.findByEmail("token@example.com").orElseThrow();

        LoginResponse response = authService.verifyOtp(verify("token@example.com", registered.getCode()));

        assertThat(response.getSessionToken()).isNotBlank();
        assertThat(response.getUserId()).isEqualTo(user.getId());
    }

    // --------------------------------------------------------------- 10
    @Test
    @DisplayName("Names are trimmed before being stored")
    void namesAreTrimmed() {
        RegistrationRequest request = new RegistrationRequest();
        request.setEmail("trim@example.com");
        request.setFirstName("  Vijay  ");
        request.setLastName("  Hosapeti  ");
        authService.register(request);

        User stored = userRepository.findByEmail("trim@example.com").orElseThrow();
        assertThat(stored.getFirstName()).isEqualTo("Vijay");
        assertThat(stored.getLastName()).isEqualTo("Hosapeti");
    }

    private RegistrationRequest request(String email) {
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