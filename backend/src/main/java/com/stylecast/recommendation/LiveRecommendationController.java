package com.stylecast.recommendation;

import com.stylecast.recommendation.dto.LiveGenerationJobResponse;
import com.stylecast.recommendation.dto.LiveRecommendationsResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Thin controller for live-Nordstrom-sourced outfit recommendations
 * (Task 8). All orchestration lives in {@link LiveRecommendationService}/
 * {@link LiveRecommendationJobService}; this controller never calls {@code
 * com.stylecast.retail} directly.
 */
@RestController
@RequestMapping("/api/events/{eventId}/recommendations/live")
public class LiveRecommendationController {

    private final LiveRecommendationService liveRecommendationService;
    private final LiveRecommendationJobService jobService;

    public LiveRecommendationController(LiveRecommendationService liveRecommendationService, LiveRecommendationJobService jobService) {
        this.liveRecommendationService = liveRecommendationService;
        this.jobService = jobService;
    }

    /**
     * Starts generation in the background and returns immediately (HTTP
     * 202) with the job's id/status - never blocks the HTTP connection on
     * the live OpenAI web-search calls {@link LiveRecommendationService#generate}
     * makes. Poll {@code GET .../status} until it reaches a terminal state
     * (COMPLETED/PARTIAL/FAILED), then fetch the result via {@code GET
     * .../recommendations/live}. Still throws synchronously (404/409) if a
     * prerequisite is missing - see {@link LiveRecommendationService#validatePrerequisites}.
     * Returns the already-active job unchanged if one is already running
     * for this event (never starts a second concurrent job).
     */
    @PostMapping("/generate")
    public ResponseEntity<LiveGenerationJobResponse> generate(@PathVariable UUID eventId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(jobService.startGenerateJob(eventId));
    }

    @GetMapping("/status")
    public LiveGenerationJobResponse getStatus(@PathVariable UUID eventId) {
        return jobService.getJobStatus(eventId);
    }

    @PostMapping("/retry-missing")
    public LiveRecommendationsResponse retryMissing(@PathVariable UUID eventId) {
        return liveRecommendationService.retryMissing(eventId);
    }

    /**
     * Marks the event's current live recommendations as stale, without
     * calling the live search provider - used by the event setup flow
     * after saved preferences changed in an interpretation-relevant way.
     */
    @PostMapping("/invalidate-stale")
    public ResponseEntity<Void> invalidateStale(@PathVariable UUID eventId) {
        liveRecommendationService.invalidateStaleRecommendations(eventId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public LiveRecommendationsResponse getRecommendations(@PathVariable UUID eventId) {
        return liveRecommendationService.getCurrent(eventId);
    }
}
