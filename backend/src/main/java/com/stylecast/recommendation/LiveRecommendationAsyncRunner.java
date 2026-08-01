package com.stylecast.recommendation;

import com.stylecast.recommendation.dto.LiveRecommendationsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Executes one live-recommendation generation job in the background.
 *
 * <p>This is a separate bean from {@link LiveRecommendationJobService}
 * specifically so {@link #runGenerate} is always invoked through the real
 * Spring {@code @Async} proxy - if {@code startGenerateJob} called this
 * method directly on {@code this} (same-class self-invocation), the
 * {@code @Async} annotation would be silently bypassed and the "background"
 * work would actually run synchronously on the calling (HTTP request)
 * thread, defeating the entire point of this job infrastructure.
 */
@Component
class LiveRecommendationAsyncRunner {

    private static final Logger log = LoggerFactory.getLogger(LiveRecommendationAsyncRunner.class);

    private final LiveRecommendationService liveRecommendationService;
    private final LiveRecommendationJobRegistry jobRegistry;

    LiveRecommendationAsyncRunner(LiveRecommendationService liveRecommendationService, LiveRecommendationJobRegistry jobRegistry) {
        this.liveRecommendationService = liveRecommendationService;
        this.jobRegistry = jobRegistry;
    }

    @Async("liveRecommendationExecutor")
    void runGenerate(UUID eventId, LiveGenerationJob job) {
        jobRegistry.update(eventId, job.withStatus(LiveGenerationJobStatus.PROCESSING, null, null, null));
        try {
            LiveRecommendationsResponse response = liveRecommendationService.generate(eventId);
            LiveGenerationJobStatus finalStatus = toJobStatus(response.status());
            jobRegistry.update(eventId, job.withStatus(finalStatus, Instant.now(), response.generation(), response.message()));
        } catch (RuntimeException e) {
            // Never leak internal exception details (stack traces, provider payloads) into
            // a job status a client can poll - only a short, safe, generic message.
            log.warn("Live recommendation generation job failed: eventId={}, jobId={}, error={}",
                    eventId, job.jobId(), e.getClass().getSimpleName());
            jobRegistry.update(eventId, job.withStatus(
                    LiveGenerationJobStatus.FAILED, Instant.now(), null, "Live recommendation generation failed unexpectedly."));
        }
    }

    /**
     * {@link LiveRecommendationCompleteness#PROVIDER_UNAVAILABLE}/{@link
     * LiveRecommendationCompleteness#NO_RESULTS} are both normal, non-error
     * outcomes the job still ran to completion for - only an actual thrown
     * exception (see {@link #runGenerate}'s catch above) is ever reported
     * as {@link LiveGenerationJobStatus#FAILED}.
     */
    private LiveGenerationJobStatus toJobStatus(LiveRecommendationCompleteness completeness) {
        return completeness == LiveRecommendationCompleteness.PARTIAL
                ? LiveGenerationJobStatus.PARTIAL
                : LiveGenerationJobStatus.COMPLETED;
    }
}
