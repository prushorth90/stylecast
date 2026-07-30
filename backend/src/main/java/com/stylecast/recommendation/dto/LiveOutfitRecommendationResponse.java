package com.stylecast.recommendation.dto;

import com.stylecast.recommendation.RecommendationSource;
import com.stylecast.recommendation.RecommendationStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Public API representation of one {@link com.stylecast.recommendation.LiveOutfitRecommendation}.
 *
 * <p>Unlike {@link OutfitRecommendationResponse}, there are no occasion/
 * weather/style/color/budget/completeness/overall fit scores - the live
 * provider does not return the product attributes (formality, tags,
 * confirmed price) those scores are computed from, and StyleCast never
 * invents a score for data it does not have.
 */
public record LiveOutfitRecommendationResponse(
        UUID id,
        UUID eventId,
        int generation,
        Integer rank,
        String name,
        RecommendationStatus status,
        RecommendationSource source,
        String explanation,
        Instant generatedAt,
        List<LiveOutfitItemResponse> items) {
}
