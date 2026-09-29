package com.bolt.checkout.service;

import com.bolt.checkout.dto.CheckoutRequest;
import com.bolt.checkout.dto.CheckoutResponse;
import com.bolt.checkout.entity.CheckoutRecord;
import com.bolt.checkout.repository.CheckoutRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CheckoutService {

    private final CheckoutRepository checkoutRepository;

    public CheckoutService(CheckoutRepository checkoutRepository) {
        this.checkoutRepository = checkoutRepository;
    }

    /**
     * @param userId authenticated user id, or {@code null} for a guest checkout.
     */
    public CheckoutResponse submitCheckout(CheckoutRequest request, Long userId) {
        CheckoutRecord record = new CheckoutRecord();
        record.setEmail(AuthService.normalizeEmail(request.getEmail()));
        record.setPhone(request.getPhone().trim());
        record.setShippingAddress(request.getShippingAddress().trim());
        record.setUserId(userId);
        checkoutRepository.save(record);

        return new CheckoutResponse(true, "Checkout submitted successfully");
    }
}