package com.bolt.checkout.exception;

/** Thrown when the login code has passed its expiry instant. */
public class OtpExpiredException extends RuntimeException {
    public OtpExpiredException(String message) {
        super(message);
    }
}
