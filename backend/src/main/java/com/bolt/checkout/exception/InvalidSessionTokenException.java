package com.bolt.checkout.exception;

/** Thrown when an endpoint needs a valid session token and did not get one. */
public class InvalidSessionTokenException extends RuntimeException {
    public InvalidSessionTokenException(String message) {
        super(message);
    }
}
