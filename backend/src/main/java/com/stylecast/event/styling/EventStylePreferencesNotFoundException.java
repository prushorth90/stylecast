package com.stylecast.event.styling;

import java.util.UUID;

/**
 * Thrown when an event exists but has no saved styling preferences yet.
 * Mapped to HTTP 404 by the global exception handler.
 */
public class EventStylePreferencesNotFoundException extends RuntimeException {

    public EventStylePreferencesNotFoundException(UUID eventId) {
        super("Style preferences not found for event: " + eventId);
    }
}
