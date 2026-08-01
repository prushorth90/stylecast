package com.stylecast.recommendation;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable snapshot of one asynchronous live-recommendation generation
 * job's state, held in memory by {@link LiveRecommendationJobRegistry} -
 * never persisted, since job state is purely a transient "is the
 * background work still running" signal (the actual generated
 * recommendations are what gets persisted, via {@link LiveOutfitRecommendation}).
 *
 * @param generation the resulting generation number once {@link
 *                    LiveGenerationJobStatus#COMPLETED}/{@link
 *                    LiveGenerationJobStatus#PARTIAL}; {@code null} until then
 * @param message    a short, safe-to-display summary (never a stack trace,
 *                    API key, or prompt content) once terminal; {@code null}
 *                    while queued/processing
 */
record LiveGenerationJob(
        UUID jobId,
        UUID eventId,
        LiveGenerationJobStatus status,
        Instant startedAt,
        Instant completedAt,
        Integer generation,
        String message) {

    LiveGenerationJob withStatus(LiveGenerationJobStatus newStatus, Instant completedAt, Integer generation, String message) {
        return new LiveGenerationJob(jobId, eventId, newStatus, startedAt, completedAt, generation, message);
    }
}
