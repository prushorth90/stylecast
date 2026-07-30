package com.stylecast.recommendation;

import java.util.UUID;

/**
 * Thrown when outfit recommendations are requested for an event that has no
 * saved {@link com.stylecast.event.styling.EventStylePreferences} yet.
 * Mapped to HTTP 409 by the global exception handler: the event exists, but
 * generation cannot proceed until the user saves preferences (budget, sizes,
 * style).
 */
public class MissingStylePreferencesException extends RuntimeException {

    public MissingStylePreferencesException(UUID eventId) {
        super("Cannot generate outfit recommendations for event " + eventId
                + ": styling preferences have not been saved yet");
    }
}
