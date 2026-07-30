package com.stylecast.recommendation;

import com.stylecast.recommendation.dto.LiveRecommendationsResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Thin controller for live-Nordstrom-sourced outfit recommendations
 * (Task 8). All orchestration lives in {@link LiveRecommendationService};
 * this controller never calls {@code com.stylecast.retail} directly.
 */
@RestController
@RequestMapping("/api/events/{eventId}/recommendations/live")
public class LiveRecommendationController {

    private final LiveRecommendationService liveRecommendationService;

    public LiveRecommendationController(LiveRecommendationService liveRecommendationService) {
        this.liveRecommendationService = liveRecommendationService;
    }

    @PostMapping("/generate")
    public LiveRecommendationsResponse generate(@PathVariable UUID eventId) {
        return liveRecommendationService.generate(eventId);
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
