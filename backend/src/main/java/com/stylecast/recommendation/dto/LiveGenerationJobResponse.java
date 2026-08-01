package com.stylecast.recommendation.dto;

import com.stylecast.recommendation.LiveGenerationJobStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Public API representation of an asynchronous live-recommendation
 * generation job's current state - returned by both {@code POST
 * .../recommendations/live/generate} (HTTP 202, immediately) and {@code GET
 * .../recommendations/live/status} (polled by the frontend every few
 * seconds until {@code status} reaches a terminal value: {@code COMPLETED},
 * {@code PARTIAL}, or {@code FAILED}).
 *
 * <p>{@code jobId} and {@code startedAt} are {@code null} when {@code
 * status} is {@code NOT_STARTED} (no job has ever been started for this
 * event). {@code generation}/{@code message} are {@code null} until the job
 * reaches a terminal state. Once terminal, the actual recommendations
 * themselves are fetched separately via {@code GET
 * .../recommendations/live} - this DTO never carries product data itself.
 */
public record LiveGenerationJobResponse(
        UUID eventId,
        UUID jobId,
        LiveGenerationJobStatus status,
        Instant startedAt,
        Instant completedAt,
        Integer generation,
        String message) {

    public static LiveGenerationJobResponse notStarted(UUID eventId) {
        return new LiveGenerationJobResponse(eventId, null, LiveGenerationJobStatus.NOT_STARTED, null, null, null, null);
    }
}
