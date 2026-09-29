package com.bolt.checkout;

import com.bolt.checkout.entity.CheckoutRecord;
import com.bolt.checkout.entity.User;
import com.bolt.checkout.repository.AuditLogRepository;
import com.bolt.checkout.repository.CheckoutRepository;
import com.bolt.checkout.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

/**
 * Base for tests that need a real database.
 *
 * <p>Deliberately <b>not</b> {@code @Transactional}. Recording a failed login code and
 * writing an audit row both commit in their own transaction, so that they survive the
 * exception that a wrong code raises. That is the behaviour production depends on, and a
 * single rolled-back test transaction would hide it: the inner transaction runs on a
 * different connection and cannot see rows the test has not committed. Isolation is
 * handled by {@link #cleanDatabase()} instead, which is closer to how the application
 * actually runs.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected CheckoutRepository checkoutRepository;

    @Autowired
    protected AuditLogRepository auditLogRepository;

    @PersistenceContext
    protected EntityManager entityManager;

    @BeforeEach
    void cleanDatabase() {
        checkoutRepository.deleteAll();
        auditLogRepository.deleteAll();
        userRepository.deleteAll();
    }

    protected User seedUser(String email, String firstName, String lastName, String otpHash) {
        User user = new User();
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setOtpHash(otpHash);
        // A seeded code has to be inside its window, otherwise verification would be
        // rejected as expired before the hash is ever compared.
        user.setOtpExpiresAt(LocalDateTime.now().plusMinutes(10));
        return userRepository.saveAndFlush(user);
    }

    /** Seeds a user whose code has already expired, for the expiry tests. */
    protected User seedExpiredUser(String email, String otpHash) {
        User user = seedUser(email, "Vijay", "Hosapeti", otpHash);
        user.setOtpExpiresAt(LocalDateTime.now().minusMinutes(1));
        return userRepository.saveAndFlush(user);
    }

    /**
     * Re-reads a user from the database, so an assertion sees stored state rather than a
     * possibly stale copy in the persistence context.
     *
     * <p>The conditional flush matters: {@code clear()} discards pending changes outright,
     * so if a test ever runs inside a transaction, dirty entities have to reach the
     * database first or they would be silently reverted instead of re-read. Outside a
     * transaction there is nothing pending, and {@code flush()} would itself throw.
     */
    protected User reload(User user) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            entityManager.flush();
        }
        entityManager.clear();
        return userRepository.findById(user.getId()).orElseThrow();
    }

    protected CheckoutRecord findCheckoutByEmail(String email) {
        return checkoutRepository.findAll().stream()
                .filter(record -> record.getEmail().equalsIgnoreCase(email))
                .reduce((first, second) -> second)
                .orElse(null);
    }
}