package com.bolt.checkout;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class CheckoutApiTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String loginAndGetToken(String email, String plainCode) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new VerifyPayload(email, plainCode))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("sessionToken").asText();
    }

    // ---------------------------------------------------------------- 1
    @Test
    @DisplayName("1. Guest checkout persists a record with user_id = null")
    void guestCheckoutStoresNullUserId() throws Exception {
        String body = json(new CheckoutPayload(
                "  Guest@Example.COM ",
                "+919876543210",
                "Bengaluru, Karnataka, India"));

        mockMvc.perform(post("/api/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Checkout submitted successfully"));

        var record = findCheckoutByEmail("guest@example.com");
        assertThat(record).isNotNull();
        assertThat(record.getUserId()).isNull();
        assertThat(record.getPhone()).isEqualTo("+919876543210");
        assertThat(record.getShippingAddress()).isEqualTo("Bengaluru, Karnataka, India");
        assertThat(record.getCreatedAt()).isNotNull();
    }

    // ---------------------------------------------------------------- 2
    @Test
    @DisplayName("2. Authenticated checkout links the record to the logged-in user")
    void authenticatedCheckoutLinksUser() throws Exception {
        var user = seedUser("vijay@example.com", "Vijay", "Hosapeti", passwordEncoder.encode("482193"));
        String token = loginAndGetToken("vijay@example.com", "482193");

        mockMvc.perform(post("/api/checkout")
                        .header("X-Session-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CheckoutPayload(
                                "Vijay@Example.com", "+919876543210", "Bengaluru"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        var record = findCheckoutByEmail("vijay@example.com");
        assertThat(record).isNotNull();
        assertThat(record.getUserId()).isEqualTo(user.getId());
    }

    // ---------------------------------------------------------------- 3
    @Test
    @DisplayName("3. A forged session token is ignored and checkout falls back to guest")
    void forgedSessionTokenFallsBackToGuest() throws Exception {
        var user = seedUser("vijay@example.com", "Vijay", "Hosapeti", passwordEncoder.encode("482193"));

        mockMvc.perform(post("/api/checkout")
                        .header("X-Session-Token", "abc.def")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CheckoutPayload("vijay@example.com", "+919876543210", "Bengaluru"))))
                .andExpect(status().isCreated());

        assertThat(findCheckoutByEmail("vijay@example.com").getUserId()).isNull();
        assertThat(user.getId()).isNotNull();
    }

    // ---------------------------------------------------------------- 4
    @Test
    @DisplayName("4. Validation failure returns 400 and persists nothing")
    void validationFailureRejected() throws Exception {
        mockMvc.perform(post("/api/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CheckoutPayload("", "", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/checkout"));

        assertThat(checkoutRepository.count()).isZero();
    }

    // ---------------------------------------------------------------- 5
    @Test
    @DisplayName("5. Missing required fields are reported individually")
    void missingFieldsReportedIndividually() throws Exception {
        mockMvc.perform(post("/api/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CheckoutPayload("guest@example.com", null, "Bengaluru"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Phone")));
    }

    // ---------------------------------------------------------------- 6
    @Test
    @DisplayName("6. Malformed JSON body returns 400 without leaking internals")
    void malformedJsonRejected() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not json "))
                .andExpect(status().isBadRequest())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        assertThat(response).doesNotContain("Exception");
        assertThat(response).doesNotContain("org.springframework");
        assertThat(response).doesNotContain("com.bolt");
    }

    // ---------------------------------------------------------------- 7
    @Test
    @DisplayName("7. Multiple checkouts for the same user create separate records")
    void repeatedCheckoutsCreateSeparateRecords() throws Exception {
        var user = seedUser("vijay@example.com", "Vijay", "Hosapeti", passwordEncoder.encode("482193"));
        String token = loginAndGetToken("vijay@example.com", "482193");

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/checkout")
                            .header("X-Session-Token", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new CheckoutPayload("vijay@example.com", "+919876543210", "Address " + i))))
                    .andExpect(status().isCreated());
        }

        assertThat(checkoutRepository.findByUserId(user.getId())).hasSize(2);
    }

    // ---------------------------------------------------------------- 8
    @Test
    @DisplayName("8. Deleting a user keeps the historical checkout record")
    void deletingUserPreservesCheckoutHistory() throws Exception {
        var user = seedUser("vijay@example.com", "Vijay", "Hosapeti", passwordEncoder.encode("482193"));
        String token = loginAndGetToken("vijay@example.com", "482193");

        mockMvc.perform(post("/api/checkout")
                        .header("X-Session-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CheckoutPayload("vijay@example.com", "+919876543210", "Bengaluru"))))
                .andExpect(status().isCreated());

        userRepository.deleteById(user.getId());
        userRepository.flush();
        // ON DELETE SET NULL is applied by the database, so drop the stale
        // first-level cache entries before asserting the persisted state.
        entityManager.clear();

        var record = findCheckoutByEmail("vijay@example.com");
        assertThat(record).isNotNull();
        assertThat(record.getUserId()).isNull();
    }

    private record CheckoutPayload(String email, String phone, String shippingAddress) {}

    private record VerifyPayload(String email, String code) {}
}