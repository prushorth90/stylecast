package com.stylecast.recommendation;

/**
 * Overall completeness of one live-Nordstrom recommendation generation
 * attempt, computed from how many required categories independently
 * succeeded in finding at least one candidate (see {@link LiveOutfitAssembler}
 * and {@link LiveRecommendationService}).
 */
public enum LiveRecommendationCompleteness {

    /** Every required category found at least one candidate; complete outfit(s) were assembled. */
    COMPLETE,

    /**
     * Some required categories found candidates and some did not - valid
     * Nordstrom candidates are still returned for the categories that
     * succeeded, but the result must never be presented as a complete
     * outfit.
     */
    PARTIAL,

    /**
     * Every required category was searched successfully (no provider
     * errors) but none found any candidates - a normal, non-error outcome.
     */
    NO_RESULTS,

    /**
     * Every attempted category search failed at the provider level (e.g.
     * timeout, misconfiguration, malformed response) - a transient outage,
     * not a genuine "nothing found" result.
     */
    PROVIDER_UNAVAILABLE
}
