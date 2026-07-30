package com.stylecast.recommendation;

import com.stylecast.catalog.InventoryRecord;
import com.stylecast.catalog.OccasionTag;
import com.stylecast.catalog.Product;
import com.stylecast.catalog.ProductCategory;
import com.stylecast.catalog.ProductVariant;
import com.stylecast.catalog.StyleTag;
import com.stylecast.catalog.WeatherTag;
import com.stylecast.event.Event;
import com.stylecast.event.EventSetting;
import com.stylecast.event.styling.EventStylePreferences;
import com.stylecast.event.styling.PreferredStyle;
import com.stylecast.occasion.InterpretationSource;
import com.stylecast.occasion.InterpretedDressCode;
import com.stylecast.occasion.OccasionClassificationResult;
import com.stylecast.occasion.OccasionInterpretation;
import com.stylecast.occasion.OccasionType;
import com.stylecast.occasion.SpecialRequirement;
import com.stylecast.weather.EventWeatherSnapshot;
import com.stylecast.weather.GeoCoordinates;
import com.stylecast.weather.GeocodedLocation;
import com.stylecast.weather.WeatherForecast;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Hand-built domain fixtures for recommendation-engine unit tests. Every
 * entity is constructed directly (no database) so tests can precisely
 * control formality, tags, stock, and prices for each hard-constraint or
 * scoring scenario under test.
 */
final class RecommendationFixtures {

    private RecommendationFixtures() {
    }

    static Product product(ProductCategory category, int formalityLevel, BigDecimal basePrice, boolean active) {
        return new Product(
                UUID.randomUUID(), "Test Brand", "Test " + category, "description", category, basePrice,
                "/image.svg", formalityLevel, active, Instant.now(), Instant.now());
    }

    static ProductVariant variant(Product product, String size, String color, BigDecimal priceOverride, int stockQuantity) {
        ProductVariant variant = new ProductVariant(
                UUID.randomUUID(), product, "SKU-" + UUID.randomUUID(), size, color, priceOverride, Instant.now());
        product.getVariants().add(variant);
        variant.getInventoryRecords().add(
                new InventoryRecord(UUID.randomUUID(), variant, "WAREHOUSE_MAIN", stockQuantity, Instant.now()));
        return variant;
    }

    static void tagOccasion(Product product, OccasionTag... tags) {
        product.getOccasionTags().addAll(List.of(tags));
    }

    static void tagStyle(Product product, StyleTag... tags) {
        product.getStyleTags().addAll(List.of(tags));
    }

    static void tagWeather(Product product, WeatherTag... tags) {
        product.getWeatherTags().addAll(List.of(tags));
    }

    static Event event() {
        return new Event(
                UUID.randomUUID(), "Test Event", "description", "Springfield",
                OffsetDateTime.now().plusDays(10), OffsetDateTime.now().plusDays(10).plusHours(3),
                EventSetting.OUTDOOR, null, Instant.now());
    }

    static EventStylePreferences preferences(
            UUID eventId, BigDecimal maxBudget, String clothingSize, String shoeSize, PreferredStyle style,
            List<String> preferredColors, List<String> colorsToAvoid) {
        EventStylePreferences preferences = new EventStylePreferences(UUID.randomUUID(), eventId, Instant.now());
        preferences.apply("Something stylish", maxBudget, clothingSize, shoeSize, style, preferredColors, colorsToAvoid, Instant.now());
        return preferences;
    }

    static OccasionInterpretation interpretation(
            UUID eventId, OccasionType occasion, int formalityLevel, List<ProductCategory> requiredCategories,
            List<ProductCategory> optionalCategories, List<String> colorsToAvoid) {
        OccasionInterpretation interpretation = new OccasionInterpretation(UUID.randomUUID(), eventId, Instant.now());
        interpretation.apply(new OccasionClassificationResult(
                occasion, InterpretedDressCode.FORMAL, formalityLevel, requiredCategories, optionalCategories,
                List.of(), colorsToAvoid, List.of(SpecialRequirement.NOT_OVERLY_FORMAL), List.of(),
                BigDecimal.valueOf(0.9), InterpretationSource.RULE_BASED_FALLBACK, null), Instant.now());
        return interpretation;
    }

    static RecommendationContext context(
            Event event, EventStylePreferences preferences, OccasionInterpretation interpretation,
            Optional<EventWeatherSnapshot> weather) {
        return new RecommendationContext(event, preferences, interpretation, weather);
    }

    /** A weather snapshot with {@link com.stylecast.weather.WeatherAvailabilityStatus#AVAILABLE} at a fixed temperature. */
    static EventWeatherSnapshot availableWeather(UUID eventId, double celsius, Integer precipitationProbability, Double windKmh) {
        EventWeatherSnapshot snapshot = new EventWeatherSnapshot(UUID.randomUUID(), eventId, Instant.now());
        GeocodedLocation location = new GeocodedLocation("Springfield", new GeoCoordinates(1.0, 2.0));
        WeatherForecast forecast = new WeatherForecast(
                OffsetDateTime.now(), OffsetDateTime.now().plusHours(3),
                BigDecimal.valueOf(celsius), BigDecimal.valueOf(celsius),
                precipitationProbability, windKmh == null ? null : BigDecimal.valueOf(windKmh), "Clear");
        snapshot.markAvailable(location, forecast, "test-provider", Instant.now());
        return snapshot;
    }
}
