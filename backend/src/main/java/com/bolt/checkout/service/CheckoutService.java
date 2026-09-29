package com.bolt.checkout.service;

import com.bolt.checkout.dto.CheckoutHistoryResponse;
import com.bolt.checkout.dto.CheckoutRequest;
import com.bolt.checkout.dto.CheckoutResponse;
import com.bolt.checkout.entity.CheckoutRecord;
import com.bolt.checkout.repository.CheckoutRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CheckoutService {

    private final CheckoutRepository checkoutRepository;
    private final AuditService auditService;

    public CheckoutService(CheckoutRepository checkoutRepository, AuditService auditService) {
        this.checkoutRepository = checkoutRepository;
        this.auditService = auditService;
    }

    /**
     * @param userId authenticated user id, or {@code null} for a guest checkout.
     */
    public CheckoutResponse submitCheckout(CheckoutRequest request, Long userId) {
        String email = AuthService.normalizeEmail(request.getEmail());

        CheckoutRecord record = new CheckoutRecord();
        record.setEmail(email);
        record.setPhone(request.getPhone().trim());
        record.setShippingAddress(request.getShippingAddress().trim());
        record.setUserId(userId);
        CheckoutRecord saved = checkoutRepository.save(record);

        // The audit row records that an order happened, never the address or phone that
        // were submitted with it.
        auditService.record(AuditService.EVENT_CHECKOUT, AuditService.OUTCOME_SUCCESS,
                email, userId, userId == null ? "guest checkout" : "checkout id=" + saved.getId());

        return new CheckoutResponse(true, "Checkout submitted successfully");
    }

    /**
     * Order history for a signed-in user, newest first.
     *
     * @param userId authenticated user id; must not be {@code null}
     */
    @Transactional(readOnly = true)
    public CheckoutHistoryResponse history(Long userId, Pageable pageable) {
        return new CheckoutHistoryResponse(
                checkoutRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable));
    }
}
