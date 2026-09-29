package com.bolt.checkout;

import com.bolt.checkout.entity.CheckoutRecord;
import com.bolt.checkout.entity.User;
import com.bolt.checkout.repository.CheckoutRepository;
import com.bolt.checkout.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public abstract class IntegrationTestBase {

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected CheckoutRepository checkoutRepository;

    @PersistenceContext
    protected EntityManager entityManager;

    @BeforeEach
    void cleanDatabase() {
        checkoutRepository.deleteAll();
        userRepository.deleteAll();
    }

    protected User seedUser(String email, String firstName, String lastName, String otpHash) {
        User user = new User();
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setOtpHash(otpHash);
        return userRepository.saveAndFlush(user);
    }

    protected CheckoutRecord findCheckoutByEmail(String email) {
        return checkoutRepository.findAll().stream()
                .filter(record -> record.getEmail().equalsIgnoreCase(email))
                .reduce((first, second) -> second)
                .orElse(null);
    }
}
