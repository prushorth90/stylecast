package com.stylecast.recommendation;

/**
 * Lifecycle status of an asynchronous live-recommendation generation job
 * (see {@link LiveRecommendationService#startGenerateJob}).
 *
 * <p>{@code NOT_STARTED} is never actually persisted/tracked - it is only
 * ever synthesized by {@link LiveRecommendationService#getJobStatus} when
 * no job has ever been started for an event, so {@code GET .../status}
 * always has something well-defined to return.
 */
public enum LiveGenerationJobStatus {
    NOT_STARTED,
    QUEUED,
    PROCESSING,
    COMPLETED,
    PARTIAL,
    FAILED
}
