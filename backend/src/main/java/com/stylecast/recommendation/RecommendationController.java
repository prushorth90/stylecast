package com.stylecast.recommendation;

import com.stylecast.recommendation.dto.RecommendationsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Thin controller for deterministic, local-catalog-only outfit
 * recommendations. All business rules live in {@link RecommendationService};
 * this module never calls a live retail provider or an LLM.
 */
@RestController
@RequestMapping("/api/events/{eventId}/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @PostMapping("/generate")
    public RecommendationsResponse generate(@PathVariable UUID eventId) {
        return recommendationService.generate(eventId);
    }

    @GetMapping
    public RecommendationsResponse getRecommendations(@PathVariable UUID eventId) {
        return recommendationService.getCurrent(eventId);
    }
}
