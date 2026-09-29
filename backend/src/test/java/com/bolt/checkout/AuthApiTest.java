package com.bolt.checkout;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuthApiTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    // ---------------------------------------------------------------- 1
    @Test
    @DisplayName("1. Registration succeeds and returns a 6-digit code")
    void registrationSucceeds() throws Exception {
        String body = json(new RegistrationPayload("vijay@example.com", "Vijay", "Hosapeti"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Registration successful"))
                .andExpect(jsonPath("$.code").isNotEmpty());

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegistrationPayload("new@example.com", "A", "B"))))
                .andReturn();

        String code = objectMapper.readTree(result.getResponse().getContentAsString()).get("code").asText();
        assertThat(code).hasSize(6).matches("\\d{6}");
        assertThat(userRepository.findByEmail("vijay@example.com")).isPresent();
    }

    // ---------------------------------------------------------------- 2
    @Test
    @DisplayName("2. Duplicate registration returns 409 Conflict")
    void duplicateRegistrationRejected() throws Exception {
        seedUser("vijay@example.com", "Vijay", "Hosapeti", passwordEncoder.encode("482193"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegistrationPayload("Vijay@Example.com", "Vijay", "Hosapeti"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email is already registered"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    // ---------------------------------------------------------------- 3
    @Test
    @DisplayName("3. Invalid registration input returns 400 Bad Request")
    void invalidRegistrationRejected() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegistrationPayload("not-an-email", "", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").isNotEmpty());

        assertThat(userRepository.count()).isZero();
    }

    // ---------------------------------------------------------------- 4
    @Test
    @DisplayName("4. Registered email is recognized")
    void registeredEmailRecognized() throws Exception {
        seedUser("vijay@example.com", "Vijay", "Hosapeti", passwordEncoder.encode("482193"));

        mockMvc.perform(get("/api/auth/recognize").param("email", "  VIJAY@Example.COM "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registered").value(true));
    }

    // ---------------------------------------------------------------- 5
    @Test
    @DisplayName("5. Unknown email is not recognized")
    void unknownEmailNotRecognized() throws Exception {
        mockMvc.perform(get("/api/auth/recognize").param("email", "nobody@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registered").value(false));
    }

    // ---------------------------------------------------------------- 6
    @Test
    @DisplayName("6. Correct OTP logs the user in")
    void correctOtpLogsIn() throws Exception {
        seedUser("vijay@example.com", "Vijay", "Hosapeti", passwordEncoder.encode("482193"));

        mockMvc.perform(post("/api/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new VerifyPayload("Vijay@Example.com", "482193"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.userId").isNumber())
                .andExpect(jsonPath("$.firstName").value("Vijay"))
                .andExpect(jsonPath("$.lastName").value("Hosapeti"))
                .andExpect(jsonPath("$.sessionToken").isNotEmpty());
    }

    // ---------------------------------------------------------------- 7
    @Test
    @DisplayName("7. Incorrect OTP returns 401 Unauthorized")
    void incorrectOtpRejected() throws Exception {
        seedUser("vijay@example.com", "Vijay", "Hosapeti", passwordEncoder.encode("482193"));

        mockMvc.perform(post("/api/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new VerifyPayload("vijay@example.com", "111111"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid login code"))
                .andExpect(jsonPath("$.status").value(401));
    }

    // ---------------------------------------------------------------- 8
    @Test
    @DisplayName("8. Malformed OTP format returns 400 Bad Request")
    void invalidOtpFormatRejected() throws Exception {
        seedUser("vijay@example.com", "Vijay", "Hosapeti", passwordEncoder.encode("482193"));

        for (String badCode : new String[]{"12345", "1234567", "abcdef"}) {
            mockMvc.perform(post("/api/auth/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new VerifyPayload("vijay@example.com", badCode))))
                    .andExpect(status().isBadRequest());
        }
    }

    // ---------------------------------------------------------------- 9
    @Test
    @DisplayName("9. Verify for an unknown email returns 404 Not Found")
    void verifyUnknownEmailReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new VerifyPayload("ghost@example.com", "482193"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // --------------------------------------------------------------- 10
    @Test
    @DisplayName("10. Only the BCrypt hash is persisted, never the plain code")
    void plainOtpIsNeverPersisted() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegistrationPayload("hash@example.com", "Hash", "Test"))))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        String plainCode = response.get("code").asText();

        String storedHash = userRepository.findByEmail("hash@example.com").orElseThrow().getOtpHash();

        assertThat(storedHash).isNotEqualTo(plainCode);
        assertThat(storedHash).startsWith("$2");
        assertThat(response.has("otpHash")).isFalse();
        assertThat(passwordEncoder.matches(plainCode, storedHash)).isTrue();
    }

    // --------------------------------------------------------------- 11
    @Test
    @DisplayName("11. Email is normalised to lower case and trimmed on registration")
    void emailIsNormalizedOnRegistration() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegistrationPayload("  Vijay@Example.COM  ", "Vijay", "Hosapeti"))))
                .andExpect(status().isCreated());

        assertThat(userRepository.findByEmail("vijay@example.com")).isPresent();
        assertThat(userRepository.findByEmail("  Vijay@Example.COM  ")).isEmpty();
    }

    // --------------------------------------------------------------- 12
    @Test
    @DisplayName("12. Health endpoint returns 200 with status UP")
    void healthEndpointIsUp() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    private record RegistrationPayload(String email, String firstName, String lastName) {}

    private record VerifyPayload(String email, String code) {}
}