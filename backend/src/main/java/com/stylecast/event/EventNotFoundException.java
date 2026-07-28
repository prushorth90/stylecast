package com.stylecast.event;

import java.util.UUID;

/**
 * Thrown when a requested event id does not exist. Mapped to HTTP 404 by the
 * global exception handler.
 */
public class EventNotFoundException extends RuntimeException {

    public EventNotFoundException(UUID eventId) {
        super("Event not found: " + eventId);
    }
}
