package com.stylecast.recommendation.dto;

import com.stylecast.recommendation.RecommendationSource;
import com.stylecast.recommendation.RecommendationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Public API representation of one {@link com.stylecast.recommendation.OutfitRecommendation}.
 */
public record OutfitRecommendationResponse(
        UUID id,
        UUID eventId,
        int generation,
        Integer rank,
        String name,
        RecommendationStatus status,
        RecommendationSource source,
        BigDecimal totalPrice,
        int occasionFitScore,
        int weatherFitScore,
        int styleFitScore,
        int colorFitScore,
        int budgetEfficiencyScore,
        int completenessScore,
        int overallScore,
        String explanation,
        Instant generatedAt,
        List<OutfitItemResponse> items) {
}
