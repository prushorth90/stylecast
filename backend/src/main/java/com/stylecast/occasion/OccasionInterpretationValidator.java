package com.stylecast.occasion;

import com.stylecast.catalog.ProductCategory;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

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
                confidence,
                InterpretationSource.AI,
                modelName);
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
