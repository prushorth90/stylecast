package com.stylecast.event.styling.dto;

import com.stylecast.event.styling.PreferredStyle;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Request body for {@code PUT /api/events/{eventId}/preferences}.
 *
 * The same request creates preferences when none exist yet, or updates the
 * existing record otherwise; the eventId comes from the path, not the body.
 */
public record UpsertEventStylePreferencesRequest(
        @NotBlank(message = "must not be blank")
        @Size(max = 2000, message = "must be at most 2000 characters")
        String outfitRequest,

        @NotNull(message = "must not be null")
        @DecimalMin(value = "0.0", inclusive = false, message = "must be greater than zero")
        @Digits(integer = 8, fraction = 2, message = "must have at most 8 integer digits and 2 fraction digits")
        BigDecimal maxBudget,

        @NotBlank(message = "must not be blank")
        @Size(max = 50, message = "must be at most 50 characters")
        String clothingSize,

        @NotBlank(message = "must not be blank")
        @Size(max = 20, message = "must be at most 20 characters")
        String shoeSize,

        @NotNull(message = "must not be null")
        PreferredStyle preferredStyle,

        List<@Size(max = 50, message = "must be at most 50 characters") String> preferredColors,

        List<@Size(max = 50, message = "must be at most 50 characters") String> colorsToAvoid
) {
}
