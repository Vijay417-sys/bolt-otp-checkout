package com.bolt.checkout.exception;

/**
 * Thrown when too many consecutive wrong codes have locked the account's OTP.
 *
 * <p>Carries the remaining lockout so the API can answer with an accurate
 * {@code Retry-After} header instead of a client guessing when to retry.
 */
public class OtpLockedException extends RuntimeException {

    private final long retryAfterSeconds;

    public OtpLockedException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = Math.max(retryAfterSeconds, 1);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
