package com.stylecast.occasion;

import com.stylecast.catalog.ProductCategory;

import java.math.BigDecimal;
import java.util.List;

/**
 * Validated, structured output of an {@link OccasionClassifier}. An instance
 * of this type is only ever constructed after every field has been checked
 * (enum membership, numeric ranges) - either by {@link OccasionInterpretationValidator}
 * for AI output, or by construction for {@link RuleBasedOccasionClassifier}.
 *
 * @param formalityLevel  1 (least formal) through 10 (most formal)
 * @param requestedItems  explicit product phrases extracted from the user's saved
 *                        {@code outfitRequest} (e.g. "USA soccer jersey") - these take
 *                        priority over {@code requiredCategories} when generating live
 *                        searches; never {@code null}, empty when no explicit item
 *                        phrases were found in the request text
 * @param confidence      0.00 through 1.00
 * @param modelName       the OpenAI model used, or {@code null} when {@code source} is
 *                        {@link InterpretationSource#RULE_BASED_FALLBACK}
 */
public record OccasionClassificationResult(
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
        String modelName
) {
    public OccasionClassificationResult {
        requestedItems = requestedItems == null ? List.of() : List.copyOf(requestedItems);
    }
}
