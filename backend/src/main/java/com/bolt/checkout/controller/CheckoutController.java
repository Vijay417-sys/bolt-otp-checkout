package com.bolt.checkout.controller;

import com.bolt.checkout.dto.CheckoutHistoryResponse;
import com.bolt.checkout.dto.CheckoutRequest;
import com.bolt.checkout.dto.CheckoutResponse;
import com.bolt.checkout.exception.InvalidSessionTokenException;
import com.bolt.checkout.service.CheckoutService;
import com.bolt.checkout.service.SessionTokenService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/checkout")
@Validated
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

    /**
     * Paged order history for the signed-in user, newest first.
     *
     * <p>Requires a valid session token: there is deliberately no guest path, because an
     * unauthenticated caller could otherwise read any account's orders by guessing an
     * email. Without a token this is 401 rather than an empty page, so the difference
     * between "no orders" and "not signed in" stays visible.
     */
    @GetMapping("/history")
    public ResponseEntity<CheckoutHistoryResponse> history(
            @RequestHeader(value = SESSION_HEADER, required = false) String sessionToken,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size) {

        Long authenticatedUserId = sessionTokenService.parse(sessionToken)
                .map(SessionTokenService.AuthenticatedUser::userId)
                .orElseThrow(() -> new InvalidSessionTokenException("A valid session token is required"));

        return ResponseEntity.ok(checkoutService.history(
                authenticatedUserId, PageRequest.of(page, size)));
    }
}