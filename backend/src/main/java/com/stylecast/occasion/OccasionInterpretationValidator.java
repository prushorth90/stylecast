package com.stylecast.occasion;

import com.stylecast.catalog.ProductCategory;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Validates a raw JSON object (parsed from {@link OpenAiOccasionClassifier}'s
 * response) into a {@link OccasionClassificationResult}, or throws
 * {@link OccasionClassificationException} - never returning or persisting a
 * partially-valid result.
 *
 * <p>Checks performed: required fields present, {@code occasion}/{@code
 * dressCode}/category/requirement values are known enum members,
 * {@code formalityLevel} is 1-10, and {@code confidence} is 0-1. This is
 * intentionally pure and I/O-free so it can be unit tested directly against
 * hand-built JSON, without a network call.
 */
public final class OccasionInterpretationValidator {

    private OccasionInterpretationValidator() {
    }

    public static OccasionClassificationResult validate(JsonNode json, String modelName) {
        if (json == null || !json.isObject()) {
            throw new OccasionClassificationException("Occasion classifier response was not a JSON object");
        }

        OccasionType occasion = parseEnum(json, "occasion", OccasionType.class);
        InterpretedDressCode dressCode = parseEnum(json, "dressCode", InterpretedDressCode.class);
        int formalityLevel = parseFormalityLevel(json);
        List<ProductCategory> requiredCategories = parseEnumList(json, "requiredCategories", ProductCategory.class);
        List<ProductCategory> optionalCategories = parseEnumList(json, "optionalCategories", ProductCategory.class);
        List<String> preferredColors = parseStringList(json, "preferredColors");
        List<String> colorsToAvoid = parseStringList(json, "colorsToAvoid");
        List<SpecialRequirement> specialRequirements = parseEnumList(json, "specialRequirements", SpecialRequirement.class);
        List<String> assumptions = parseStringList(json, "assumptions");
        List<RequestedItem> requestedItems = parseRequestedItems(json);
        BigDecimal confidence = parseConfidence(json);

        return new OccasionClassificationResult(
                occasion,
                dressCode,
                formalityLevel,
                requiredCategories,
                optionalCategories,
                preferredColors,
                colorsToAvoid,
                specialRequirements,
                assumptions,
                requestedItems,
                confidence,
                InterpretationSource.AI,
                modelName);
    }

    /**
     * Parses the model's {@code requestedItems} array into validated {@link
     * RequestedItem}s via {@link RequestedItemNormalizer}. A missing/absent
     * field yields an empty list (not every event has explicit product
     * phrases) rather than failing the whole classification, and an item
     * with a blank/missing {@code originalPhrase} is silently skipped (same
     * leniency as other blank-entry handling in this class) - but an
     * unknown {@code genericCategory} value throws, the same strict
     * convention {@link #parseEnumList} already uses for enum arrays, since
     * this is model output that must never be persisted when invalid.
     *
     * <p>Deterministic parsing runs as a safety net over each AI-provided
     * item: {@link RequestedItemPhraseSplitter#splitPhrase} re-checks
     * whether {@code originalPhrase} actually merges more than one
     * recognized garment (the model can occasionally do this the same way
     * the rule-based fallback used to, e.g. returning a single {@code
     * "shirt trousers shoes"} item). When it does, the merged item is
     * replaced by the individually recognized ones instead of trusting the
     * model's single category; when the phrase isn't a recognized multi-
     * garment merge (including phrases our deterministic keywords don't
     * recognize at all), the model's own phrase/category are trusted as-is
     * - LLM assistance only kicks in where deterministic parsing has
     * nothing to say.
     */
    private static List<RequestedItem> parseRequestedItems(JsonNode json) {
        JsonNode node = json.path("requestedItems");
        if (!node.isArray()) {
            return List.of();
        }
        List<RequestedItem> items = new ArrayList<>();
        int displayOrder = 0;
        for (JsonNode itemNode : node) {
            if (!itemNode.isObject()) {
                continue;
            }
            String originalPhrase = itemNode.path("originalPhrase").asString(null);
            if (originalPhrase == null || originalPhrase.isBlank()) {
                continue;
            }
            GenericItemCategory genericCategory = parseGenericItemCategory(itemNode.path("genericCategory"));
            List<String> searchTerms = parseStringList(itemNode, "searchTerms");
            Boolean required = itemNode.path("required").isBoolean() ? itemNode.path("required").asBoolean() : null;
            String activityContext = itemNode.path("activityContext").asString(null);

            List<RequestedItemPhraseSplitter.SplitItem> split =
                    RequestedItemPhraseSplitter.splitPhrase(originalPhrase, activityContext);
            if (split.size() <= 1) {
                // Not a recognized multi-garment merge (or nothing our deterministic
                // keywords recognize at all) - trust the model's own phrase/category.
                RequestedItem item = RequestedItemNormalizer.normalize(
                        originalPhrase, genericCategory, searchTerms, required, activityContext, displayOrder);
                if (item != null) {
                    items.add(item);
                    displayOrder++;
                }
            } else {
                // The model merged several distinct garments into one item - split it,
                // discarding the model's single category in favor of the ones actually
                // present. Search terms were built for the merged phrase, not each part,
                // so they are intentionally not reused here (each sub-item falls back to
                // its own phrase as its search term via RequestedItemNormalizer).
                for (RequestedItemPhraseSplitter.SplitItem splitItem : split) {
                    RequestedItem item = RequestedItemNormalizer.normalize(
                            splitItem.phrase(), splitItem.category(), List.of(), required, activityContext, displayOrder);
                    if (item != null) {
                        items.add(item);
                        displayOrder++;
                    }
                }
            }
        }
        return List.copyOf(items);
    }


    private static GenericItemCategory parseGenericItemCategory(JsonNode node) {
        String raw = node.isValueNode() ? node.asString(null) : null;
        if (raw == null || raw.isBlank()) {
            throw new OccasionClassificationException("Missing required field: requestedItems[].genericCategory");
        }
        try {
            return GenericItemCategory.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new OccasionClassificationException("Unknown genericCategory value in requestedItems: " + raw);
        }
    }

    private static int parseFormalityLevel(JsonNode json) {
        JsonNode node = json.path("formalityLevel");
        if (!node.isNumber()) {
            throw new OccasionClassificationException("Missing or non-numeric required field: formalityLevel");
        }
        int value = node.asInt();
        if (value < 1 || value > 10) {
            throw new OccasionClassificationException("formalityLevel out of range 1-10: " + value);
        }
        return value;
    }

    private static BigDecimal parseConfidence(JsonNode json) {
        JsonNode node = json.path("confidence");
        if (!node.isNumber()) {
            throw new OccasionClassificationException("Missing or non-numeric required field: confidence");
        }
        double value = node.asDouble();
        if (value < 0.0 || value > 1.0) {
            throw new OccasionClassificationException("confidence out of range 0-1: " + value);
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static <E extends Enum<E>> E parseEnum(JsonNode json, String field, Class<E> type) {
        JsonNode node = json.path(field);
        String raw = node.isValueNode() ? node.asString(null) : null;
        if (raw == null || raw.isBlank()) {
            throw new OccasionClassificationException("Missing required field: " + field);
        }
        try {
            return Enum.valueOf(type, raw);
        } catch (IllegalArgumentException e) {
            throw new OccasionClassificationException("Unknown " + field + " value: " + raw);
        }
    }

    private static <E extends Enum<E>> List<E> parseEnumList(JsonNode json, String field, Class<E> type) {
        JsonNode node = json.path(field);
        if (!node.isArray()) {
            throw new OccasionClassificationException("Missing or non-array required field: " + field);
        }
        List<E> values = new ArrayList<>();
        for (JsonNode item : node) {
            String raw = item.isValueNode() ? item.asString(null) : null;
            if (raw == null || raw.isBlank()) {
                continue;
            }
            try {
                values.add(Enum.valueOf(type, raw));
            } catch (IllegalArgumentException e) {
                throw new OccasionClassificationException("Unknown value in " + field + ": " + raw);
            }
        }
        return List.copyOf(values);
    }

    private static List<String> parseStringList(JsonNode json, String field) {
        JsonNode node = json.path(field);
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String raw = item.isValueNode() ? item.asString(null) : null;
            if (raw != null && !raw.isBlank()) {
                values.add(raw.trim());
            }
        }
        return List.copyOf(values);
    }
}
