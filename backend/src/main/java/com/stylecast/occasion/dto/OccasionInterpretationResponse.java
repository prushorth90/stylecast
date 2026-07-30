package com.stylecast.occasion.dto;

import com.stylecast.catalog.ProductCategory;
import com.stylecast.occasion.InterpretationSource;
import com.stylecast.occasion.InterpretedDressCode;
import com.stylecast.occasion.OccasionInterpretation;
import com.stylecast.occasion.OccasionType;
import com.stylecast.occasion.RequestedItem;
import com.stylecast.occasion.SpecialRequirement;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Public API representation of {@link OccasionInterpretation}.
 */
public record OccasionInterpretationResponse(
        UUID id,
        UUID eventId,
        OccasionType occasion,
        InterpretedDressCode dressCode,
        int formalityLevel,
        List<ProductCategory> requiredCategories,
        List<ProductCategory> optionalCategories,
        List<String> preferredColors,
        List<String> colorsToAvoid,
        List<SpecialRequirement> specialRequirements,
        List<String> assumptions,
        List<RequestedItem> requestedItems,
        BigDecimal confidence,
        InterpretationSource source,
        Instant generatedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static OccasionInterpretationResponse fromEntity(OccasionInterpretation interpretation) {
        return new OccasionInterpretationResponse(
                interpretation.getId(),
                interpretation.getEventId(),
                interpretation.getOccasion(),
                interpretation.getDressCode(),
                interpretation.getFormalityLevel(),
                interpretation.getRequiredCategories(),
                interpretation.getOptionalCategories(),
                interpretation.getPreferredColors(),
                interpretation.getColorsToAvoid(),
                interpretation.getSpecialRequirements(),
                interpretation.getAssumptions(),
                interpretation.getRequestedItems(),
                interpretation.getConfidence(),
                interpretation.getSource(),
                interpretation.getGeneratedAt(),
                interpretation.getCreatedAt(),
                interpretation.getUpdatedAt());
    }
}
