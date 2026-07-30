package com.stylecast.recommendation.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Public API representation of an event's current outfit recommendations
 * (the latest generation only). {@code hasResults=false} is a normal,
 * expected outcome - not an error - whenever no valid outfit could be
 * assembled, or nothing has been generated yet.
 */
public record RecommendationsResponse(
        UUID eventId,
        int generation,
        Instant generatedAt,
        boolean hasResults,
        String noResultReason,
        List<OutfitRecommendationResponse> recommendations) {

    public static RecommendationsResponse notGeneratedYet(UUID eventId) {
        return new RecommendationsResponse(
                eventId, 0, null, false, "Recommendations have not been generated yet for this event.", List.of());
    }
}
