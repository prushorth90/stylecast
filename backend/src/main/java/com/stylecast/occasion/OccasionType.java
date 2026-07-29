package com.stylecast.occasion;

/**
 * Classified occasion type for an event, produced by an {@link OccasionClassifier}.
 *
 * {@link #UNKNOWN} is a valid, expected result when the available event
 * text does not provide enough evidence to classify confidently - it is
 * always preferred over guessing.
 */
public enum OccasionType {
    WEDDING,
    INTERVIEW,
    BUSINESS_MEETING,
    NETWORKING,
    DINNER,
    DATE,
    CONCERT,
    PARTY,
    CONFERENCE,
    CASUAL_OUTING,
    FORMAL_EVENT,
    UNKNOWN
}
