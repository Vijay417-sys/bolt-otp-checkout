package com.bolt.checkout.controller;

import com.bolt.checkout.dto.CheckoutRequest;
import com.bolt.checkout.dto.CheckoutResponse;
import com.bolt.checkout.service.CheckoutService;
import com.bolt.checkout.service.SessionTokenService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/checkout")
public class CheckoutController {

    private static final String SESSION_HEADER = "X-Session-Token";

    private final CheckoutService checkoutService;
    private final SessionTokenService sessionTokenService;

    public CheckoutController(CheckoutService checkoutService, SessionTokenService sessionTokenService) {
        this.checkoutService = checkoutService;
        this.sessionTokenService = sessionTokenService;
    }

    /**
     * Persists a checkout record. The linked user id is derived from the signed session token
     * issued by {@code POST /api/auth/verify}; a missing or invalid token results in a guest
     * checkout with {@code user_id = null}.
     */
    @PostMapping
    public ResponseEntity<CheckoutResponse> checkout(
            @Valid @RequestBody CheckoutRequest request,
            @RequestHeader(value = SESSION_HEADER, required = false) String sessionToken) {

        Long authenticatedUserId = sessionTokenService.parse(sessionToken)
                .map(SessionTokenService.AuthenticatedUser::userId)
                .orElse(null);

        CheckoutResponse response = checkoutService.submitCheckout(request, authenticatedUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}