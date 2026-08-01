package com.stylecast.recommendation;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry of the current (most recent) asynchronous
 * generation job per event - one active job per event at a time.
 *
 * <p>Deliberately NOT persisted: job state is a transient "is the
 * background work still running" signal only, scoped to this single
 * application instance's lifetime (a restart simply forgets in-flight
 * jobs, which is acceptable since the underlying generation itself is
 * re-triggerable by the user). The actual generated recommendations are
 * persisted separately via {@link LiveOutfitRecommendation}, independently
 * of this registry.
 */
@Component
class LiveRecommendationJobRegistry {

    /** Result of attempting to start a job: the job (new or already-active) and whether a new one was actually created. */
    record StartResult(LiveGenerationJob job, boolean newlyStarted) {
    }

    private final Map<UUID, LiveGenerationJob> jobsByEvent = new ConcurrentHashMap<>();

    Optional<LiveGenerationJob> current(UUID eventId) {
        return Optional.ofNullable(jobsByEvent.get(eventId));
    }

    /**
     * Atomically starts a new {@link LiveGenerationJobStatus#QUEUED} job for
     * {@code eventId}, UNLESS one is already {@code QUEUED}/{@code
     * PROCESSING} - in that case the existing (still-active) job is
     * returned instead and {@code newlyStarted} is {@code false}, which is
     * what prevents duplicate concurrent jobs for the same event.
     */
    synchronized StartResult startIfNotActive(UUID eventId) {
        LiveGenerationJob existing = jobsByEvent.get(eventId);
        if (existing != null && isActive(existing.status())) {
            return new StartResult(existing, false);
        }
        LiveGenerationJob job = new LiveGenerationJob(
                UUID.randomUUID(), eventId, LiveGenerationJobStatus.QUEUED, Instant.now(), null, null, null);
        jobsByEvent.put(eventId, job);
        return new StartResult(job, true);
    }

    /** Replaces the tracked job for {@code eventId} - a no-op if a NEWER job has since been started for the same event (stale update). */
    void update(UUID eventId, LiveGenerationJob job) {
        jobsByEvent.merge(eventId, job, (current, candidate) ->
                current.jobId().equals(candidate.jobId()) ? candidate : current);
    }

    private boolean isActive(LiveGenerationJobStatus status) {
        return status == LiveGenerationJobStatus.QUEUED || status == LiveGenerationJobStatus.PROCESSING;
    }
}
