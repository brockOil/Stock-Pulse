package com.stockpulse.exception;

/** Thrown when a request would violate a state-machine invariant, e.g. deciding a suggestion twice. */
public class InvalidStateException extends RuntimeException {
    public InvalidStateException(String message) {
        super(message);
    }
}
