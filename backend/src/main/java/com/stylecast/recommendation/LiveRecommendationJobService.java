package com.stylecast.recommendation;

import com.stylecast.recommendation.dto.LiveGenerationJobResponse;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Orchestrates asynchronous live-recommendation generation: starting a
 * background job (never blocking the HTTP request thread on the
 * potentially slow live OpenAI web-search calls {@link
 * LiveRecommendationService#generate} makes) and tracking its status via
 * {@link LiveRecommendationJobRegistry}. The actual background work runs
 * in {@link LiveRecommendationAsyncRunner} - a separate bean, so its
 * {@code @Async} method is always invoked through the real Spring proxy
 * rather than via same-class self-invocation (which would silently make
 * it run synchronously, defeating the point of this whole class).
 */
@Service
public class LiveRecommendationJobService {

    private final LiveRecommendationService liveRecommendationService;
    private final LiveRecommendationJobRegistry jobRegistry;
    private final RecommendationContextLoader contextLoader;
    private final LiveRecommendationAsyncRunner asyncRunner;

    public LiveRecommendationJobService(
            LiveRecommendationService liveRecommendationService,
            LiveRecommendationJobRegistry jobRegistry,
            RecommendationContextLoader contextLoader,
            LiveRecommendationAsyncRunner asyncRunner) {
        this.liveRecommendationService = liveRecommendationService;
        this.jobRegistry = jobRegistry;
        this.contextLoader = contextLoader;
        this.asyncRunner = asyncRunner;
    }

    /**
     * Validates the event's prerequisites synchronously (so an invalid
     * request - unknown event, missing preferences/interpretation - is
     * rejected immediately with the usual 404/409, never merely surfacing
     * later as a {@link LiveGenerationJobStatus#FAILED} job), then starts a
     * background generation job and returns immediately. Returns the
     * already-active job unchanged (never starting a second one) if one is
     * already {@code QUEUED}/{@code PROCESSING} for this event.
     */
    public LiveGenerationJobResponse startGenerateJob(UUID eventId) {
        liveRecommendationService.validatePrerequisites(eventId);

        LiveRecommendationJobRegistry.StartResult startResult = jobRegistry.startIfNotActive(eventId);
        if (startResult.newlyStarted()) {
            asyncRunner.runGenerate(eventId, startResult.job());
        }
        return toResponse(startResult.job());
    }

    /** Returns {@link LiveGenerationJobStatus#NOT_STARTED} if no job has ever been started for this event. */
    public LiveGenerationJobResponse getJobStatus(UUID eventId) {
        contextLoader.requireEvent(eventId);
        return jobRegistry.current(eventId)
                .map(this::toResponse)
                .orElse(LiveGenerationJobResponse.notStarted(eventId));
    }

    private LiveGenerationJobResponse toResponse(LiveGenerationJob job) {
        return new LiveGenerationJobResponse(
                job.eventId(), job.jobId(), job.status(), job.startedAt(), job.completedAt(), job.generation(), job.message());
    }
}
