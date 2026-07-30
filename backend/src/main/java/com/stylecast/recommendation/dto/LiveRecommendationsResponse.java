package com.stylecast.recommendation.dto;

import com.stylecast.catalog.ProductCategory;
import com.stylecast.recommendation.LiveRecommendationCompleteness;
import com.stylecast.recommendation.RequestedItemSummary;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Public API representation of an event's current live-Nordstrom outfit
 * recommendations (the latest generation only).
 *
 * <p>{@code status} is one of:
 * <ul>
 *   <li>{@link LiveRecommendationCompleteness#COMPLETE} - every required
 *       category found candidates; {@code recommendations} contains
 *       complete outfits.</li>
 *   <li>{@link LiveRecommendationCompleteness#PARTIAL} - some required
 *       categories found candidates and some did not; {@code
 *       recommendations} still contains valid Nordstrom candidates for the
 *       categories that succeeded, but must never be presented as a
 *       complete outfit - see {@code missingCategories} and {@code
 *       message}.</li>
 *   <li>{@link LiveRecommendationCompleteness#NO_RESULTS} - every required
 *       category was searched successfully but none found anything; a
 *       normal, non-error outcome, not a fabricated outfit.</li>
 *   <li>{@link LiveRecommendationCompleteness#PROVIDER_UNAVAILABLE} - every
 *       attempted search failed at the provider level (a transient
 *       outage); retrying later (or via {@code retry-missing}) may help.</li>
 * </ul>
 *
 * <p>{@code foundRequestedItems}/{@code missingRequestedItems} (Task 8.5)
 * are populated instead of {@code foundCategories}/{@code missingCategories}
 * whenever the event's occasion interpretation extracted explicit product
 * phrases - the two pairs are mutually exclusive per generation. A missing
 * explicit item is never silently dropped or substituted with an unrelated
 * product; it is always reported here.
 */
public record LiveRecommendationsResponse(
        UUID eventId,
        int generation,
        Instant generatedAt,
        LiveRecommendationCompleteness status,
        List<ProductCategory> foundCategories,
        List<ProductCategory> missingCategories,
        List<RequestedItemSummary> foundRequestedItems,
        List<RequestedItemSummary> missingRequestedItems,
        String message,
        List<LiveOutfitRecommendationResponse> recommendations) {

    public static LiveRecommendationsResponse notGeneratedYet(UUID eventId) {
        return new LiveRecommendationsResponse(
                eventId, 0, null, LiveRecommendationCompleteness.NO_RESULTS, List.of(), List.of(), List.of(), List.of(),
                "Live recommendations have not been generated yet for this event.", List.of());
    }
}
