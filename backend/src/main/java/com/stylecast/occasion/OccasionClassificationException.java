package com.stylecast.occasion;

/**
 * Thrown by {@link OpenAiOccasionClassifier} when it cannot produce a valid
 * result: no API key configured, a network/timeout failure, a non-success
 * response, malformed JSON, or output that fails
 * {@link OccasionInterpretationValidator}.
 *
 * <p>This exception is internal to the occasion module - it is always caught
 * by {@link OccasionInterpretationService}, which falls back to
 * {@link RuleBasedOccasionClassifier}, and never propagates to a controller
 * or an HTTP response.
 */
public class OccasionClassificationException extends RuntimeException {

    public OccasionClassificationException(String message) {
        super(message);
    }

    public OccasionClassificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
