package com.stylecast.occasion;

/**
 * Interpreted dress code for an event, produced by an {@link OccasionClassifier}.
 *
 * {@link #UNKNOWN} is a valid, expected result when there isn't enough
 * evidence (no manually entered dress code and no strong contextual signal)
 * to interpret a dress code confidently.
 */
public enum InterpretedDressCode {
    CASUAL,
    SMART_CASUAL,
    BUSINESS_CASUAL,
    COCKTAIL,
    GARDEN_COCKTAIL,
    BUSINESS_FORMAL,
    FORMAL,
    BLACK_TIE,
    UNKNOWN
}
