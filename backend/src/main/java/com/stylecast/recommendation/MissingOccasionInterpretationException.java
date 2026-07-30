package com.stylecast.recommendation;

import java.util.UUID;

/**
 * Thrown when outfit recommendations are requested for an event that has no
 * {@link com.stylecast.occasion.OccasionInterpretation} yet. Mapped to HTTP
 * 409 by the global exception handler: the event exists, but generation
 * cannot proceed until an occasion interpretation is available (required
 * categories, formality, colors).
 */
public class MissingOccasionInterpretationException extends RuntimeException {

    public MissingOccasionInterpretationException(UUID eventId) {
        super("Cannot generate outfit recommendations for event " + eventId
                + ": no occasion interpretation is available yet");
    }
}
