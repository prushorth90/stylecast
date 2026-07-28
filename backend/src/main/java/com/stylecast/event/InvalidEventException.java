package com.stylecast.event;

/**
 * Thrown when an event request violates a domain rule that isn't expressible
 * as a simple per-field bean validation constraint (e.g. end time not after
 * start time). Mapped to HTTP 400 by the global exception handler.
 */
public class InvalidEventException extends RuntimeException {

    public InvalidEventException(String message) {
        super(message);
    }
}
