package com.stylecast.event.styling.dto;

import com.stylecast.event.styling.EventStylePreferences;
import com.stylecast.event.styling.PreferredStyle;
import com.stylecast.event.styling.ShoppingDepartment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Public API representation of {@link EventStylePreferences}.
 *
 * <p>{@code interpretationRefreshRecommended} is only ever {@code true} on
 * the response to a {@code PUT} that changed an interpretation-relevant
 * field (outfitRequest/preferredStyle/preferredColors/colorsToAvoid) on an
 * ALREADY-existing record - never on the first save for an event (nothing
 * to compare against yet - the occasion interpretation auto-generates from
 * the just-saved values on its own first {@code GET}) and never on a plain
 * {@code GET}. The frontend's two-step setup modal uses this flag to decide
 * whether to explicitly call {@code POST .../interpretation/regenerate} and
 * {@code POST .../recommendations/live/invalidate-stale} after saving.
 */
public record EventStylePreferencesResponse(
        UUID id,
        UUID eventId,
        String outfitRequest,
        BigDecimal maxBudget,
        String clothingSize,
        String shoeSize,
        PreferredStyle preferredStyle,
        List<String> preferredColors,
        List<String> colorsToAvoid,
        ShoppingDepartment shoppingDepartment,
        Instant createdAt,
        Instant updatedAt,
        boolean interpretationRefreshRecommended
) {
    public static EventStylePreferencesResponse fromEntity(EventStylePreferences preferences) {
        return fromEntity(preferences, false);
    }

    public static EventStylePreferencesResponse fromEntity(
            EventStylePreferences preferences, boolean interpretationRefreshRecommended) {
        return new EventStylePreferencesResponse(
                preferences.getId(),
                preferences.getEventId(),
                preferences.getOutfitRequest(),
                preferences.getMaxBudget(),
                preferences.getClothingSize(),
                preferences.getShoeSize(),
                preferences.getPreferredStyle(),
                preferences.getPreferredColors(),
                preferences.getColorsToAvoid(),
                preferences.getShoppingDepartment(),
                preferences.getCreatedAt(),
                preferences.getUpdatedAt(),
                interpretationRefreshRecommended);
    }
}
