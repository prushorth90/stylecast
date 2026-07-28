package com.stylecast.event.styling.dto;

import com.stylecast.event.styling.EventStylePreferences;
import com.stylecast.event.styling.PreferredStyle;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Public API representation of {@link EventStylePreferences}.
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
        Instant createdAt,
        Instant updatedAt
) {
    public static EventStylePreferencesResponse fromEntity(EventStylePreferences preferences) {
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
                preferences.getCreatedAt(),
                preferences.getUpdatedAt());
    }
}
