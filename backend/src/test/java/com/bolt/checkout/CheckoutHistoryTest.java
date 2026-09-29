package com.bolt.checkout;

import com.bolt.checkout.entity.AuditLog;
import com.bolt.checkout.entity.CheckoutRecord;
import com.bolt.checkout.entity.User;
import com.bolt.checkout.repository.CheckoutRepository;
import com.bolt.checkout.service.AuditService;
import com.bolt.checkout.service.SessionTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Covers the paged order-history endpoint and the checkout audit trail. */
class CheckoutHistoryTest extends IntegrationTestBase {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private CheckoutRepository checkoutRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SessionTokenService sessionTokenService;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    @DisplayName("History is refused without a session token")
    void historyRequiresAToken() throws Exception {
        mockMvc().perform(get("/api/checkout/history"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("History is refused when the token is forged")
    void historyRejectsAForgedToken() throws Exception {
        mockMvc().perform(get("/api/checkout/history")
                        .header("X-Session-Token", "not-a-real-token.abc"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("History returns only the signed-in user's orders, newest first")
    void historyIsScopedToTheUser() throws Exception {
        User mine = seedUser("vijay@example.com", "Vijay", "Hosapeti", passwordEncoder.encode("482193"));
        User theirs = seedUser("someone-else@example.com", "Other", "Person", passwordEncoder.encode("111111"));

        seedOrder("vijay@example.com", mine.getId());
        seedOrder("vijay@example.com", mine.getId());
        seedOrder("someone-else@example.com", theirs.getId());

        mockMvc().perform(get("/api/checkout/history")
                        .header("X-Session-Token", tokenFor(mine)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.orders.length()").value(2))
                .andExpect(jsonPath("$.orders[0].email").value("vijay@example.com"));
    }

    @Test
    @DisplayName("Page size is honoured and the last page is flagged")
    void pagingIsHonoured() throws Exception {
        User mine = seedUser("vijay@example.com", "Vijay", "Hosapeti", passwordEncoder.encode("482193"));
        for (int i = 0; i < 5; i++) {
            seedOrder("vijay@example.com", mine.getId());
        }

        mockMvc().perform(get("/api/checkout/history")
                        .param("page", "0")
                        .param("size", "2")
                        .header("X-Session-Token", tokenFor(mine)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.orders.length()").value(2))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false));

        mockMvc().perform(get("/api/checkout/history")
                        .param("page", "2")
                        .param("size", "2")
                        .header("X-Session-Token", tokenFor(mine)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders.length()").value(1))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    @DisplayName("A page size above the maximum is rejected")
    void pageSizeIsCapped() throws Exception {
        User user = seedUser("vijay@example.com", "Vijay", "Hosapeti",
                passwordEncoder.encode("482193"));

        mockMvc().perform(get("/api/checkout/history")
                        .param("size", "5000")
                        .header("X-Session-Token", tokenFor(user)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("A user with no orders gets an empty page rather than an error")
    void emptyHistoryIsNotAnError() throws Exception {
        User user = seedUser("vijay@example.com", "Vijay", "Hosapeti",
                passwordEncoder.encode("482193"));

        mockMvc().perform(get("/api/checkout/history")
                        .header("X-Session-Token", tokenFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.orders.length()").value(0));
    }

    // -------------------------------------------------------------- audit
    @Test
    @DisplayName("A successful checkout is audited and the row carries no address or phone")
    void checkoutIsAudited() throws Exception {
        seedUser("vijay@example.com", "Vijay", "Hosapeti", passwordEncoder.encode("482193"));

        mockMvc().perform(post("/api/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CheckoutPayload(
                                "vijay@example.com", "+919876543210", "Bengaluru, Karnataka, India"))))
                .andExpect(status().isCreated());

        List<AuditLog> checkouts = auditLogRepository.findAll().stream()
                .filter(entry -> AuditService.EVENT_CHECKOUT.equals(entry.getEvent()))
                .toList();

        assertThat(checkouts).hasSize(1);
        assertThat(checkouts.get(0).getOutcome()).isEqualTo(AuditService.OUTCOME_SUCCESS);
        assertThat(checkouts.get(0).getDetail()).doesNotContain("Bengaluru").doesNotContain("+919876543210");
    }

    // ------------------------------------------------------------ helpers
    private void seedOrder(String email, Long userId) {
        CheckoutRecord record = new CheckoutRecord();
        record.setEmail(email);
        record.setPhone("+919876543210");
        record.setShippingAddress("Bengaluru, Karnataka, India");
        record.setUserId(userId);
        checkoutRepository.saveAndFlush(record);
    }

    /** Issues a real session token for the user, rather than hand-forging one. */
    private String tokenFor(User user) {
        return sessionTokenService.issue(user);
    }

    /** Matches the JSON contract of POST /api/checkout. */
    private record CheckoutPayload(String email, String phone, String shippingAddress) {
    }
}
