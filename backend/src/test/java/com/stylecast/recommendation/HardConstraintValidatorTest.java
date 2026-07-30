package com.stylecast.recommendation;

import com.stylecast.catalog.ProductCategory;
import com.stylecast.catalog.WeatherTag;
import com.stylecast.event.styling.PreferredStyle;
import com.stylecast.occasion.OccasionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for every hard constraint rule in {@link HardConstraintValidator},
 * using hand-built catalog/context fixtures (no database).
 */
class HardConstraintValidatorTest {

    private final HardConstraintValidator validator = new HardConstraintValidator();

    private RecommendationContext contextWithBudget(BigDecimal budget) {
        var event = RecommendationFixtures.event();
        var preferences = RecommendationFixtures.preferences(
                event.getId(), budget, "M", "9", PreferredStyle.CLASSIC, List.of(), List.of("red"));
        var interpretation = RecommendationFixtures.interpretation(
                event.getId(), OccasionType.WEDDING, 8, List.of(ProductCategory.SUIT), List.of(), List.of("red"));
        return RecommendationFixtures.context(event, preferences, interpretation, Optional.empty());
    }

    @Test
    void validateItem_withInactiveProduct_isRejected() {
        var context = contextWithBudget(BigDecimal.valueOf(1000));
        var product = RecommendationFixtures.product(ProductCategory.SUIT, 8, BigDecimal.valueOf(400), false);
        var variant = RecommendationFixtures.variant(product, "M", "Navy", null, 5);

        List<String> violations = validator.validateItem(ProductCategory.SUIT, product, variant, context);

        assertThat(violations).anyMatch(v -> v.contains("not active"));
    }

    @Test
    void validateItem_withOutOfStockVariant_isRejected() {
        var context = contextWithBudget(BigDecimal.valueOf(1000));
        var product = RecommendationFixtures.product(ProductCategory.SUIT, 8, BigDecimal.valueOf(400), true);
        var variant = RecommendationFixtures.variant(product, "M", "Navy", null, 0);

        List<String> violations = validator.validateItem(ProductCategory.SUIT, product, variant, context);

        assertThat(violations).anyMatch(v -> v.contains("in-stock"));
    }

    @Test
    void validateItem_withMismatchedClothingSize_isRejected() {
        var context = contextWithBudget(BigDecimal.valueOf(1000));
        var product = RecommendationFixtures.product(ProductCategory.SUIT, 8, BigDecimal.valueOf(400), true);
        var variant = RecommendationFixtures.variant(product, "L", "Navy", null, 5);

        List<String> violations = validator.validateItem(ProductCategory.SUIT, product, variant, context);

        assertThat(violations).anyMatch(v -> v.contains("does not match requested size"));
    }

    @Test
    void validateItem_withMismatchedShoeSize_isRejected() {
        var context = contextWithBudget(BigDecimal.valueOf(1000));
        var product = RecommendationFixtures.product(ProductCategory.SHOES, 8, BigDecimal.valueOf(150), true);
        var variant = RecommendationFixtures.variant(product, "10", "Navy", null, 5);

        List<String> violations = validator.validateItem(ProductCategory.SHOES, product, variant, context);

        assertThat(violations).anyMatch(v -> v.contains("does not match requested size"));
    }

    @Test
    void validateItem_withMatchingShoeSize_isAccepted() {
        var context = contextWithBudget(BigDecimal.valueOf(1000));
        var product = RecommendationFixtures.product(ProductCategory.SHOES, 8, BigDecimal.valueOf(150), true);
        var variant = RecommendationFixtures.variant(product, "9", "Navy", null, 5);

        assertThat(validator.validateItem(ProductCategory.SHOES, product, variant, context)).isEmpty();
    }

    @Test
    void validateItem_accessoryIgnoresClothingSize() {
        var context = contextWithBudget(BigDecimal.valueOf(1000));
        var product = RecommendationFixtures.product(ProductCategory.ACCESSORY, 8, BigDecimal.valueOf(50), true);
        var variant = RecommendationFixtures.variant(product, "ONE_SIZE", "Navy", null, 5);

        assertThat(validator.validateItem(ProductCategory.ACCESSORY, product, variant, context)).isEmpty();
    }

    @Test
    void validateItem_withAvoidedColor_isRejected() {
        var context = contextWithBudget(BigDecimal.valueOf(1000));
        var product = RecommendationFixtures.product(ProductCategory.SUIT, 8, BigDecimal.valueOf(400), true);
        var variant = RecommendationFixtures.variant(product, "M", "Red", null, 5);

        List<String> violations = validator.validateItem(ProductCategory.SUIT, product, variant, context);

        assertThat(violations).anyMatch(v -> v.contains("avoided color"));
    }

    @Test
    void validateItem_withColorMatchCaseInsensitive_isRejected() {
        var context = contextWithBudget(BigDecimal.valueOf(1000));
        var product = RecommendationFixtures.product(ProductCategory.SUIT, 8, BigDecimal.valueOf(400), true);
        var variant = RecommendationFixtures.variant(product, "M", "RED", null, 5);

        assertThat(validator.validateItem(ProductCategory.SUIT, product, variant, context)).isNotEmpty();
    }

    @Test
    void validateItem_withFormalityTooLow_isRejected() {
        var context = contextWithBudget(BigDecimal.valueOf(1000)); // interpretation formality = 8
        var product = RecommendationFixtures.product(ProductCategory.SUIT, 3, BigDecimal.valueOf(400), true);
        var variant = RecommendationFixtures.variant(product, "M", "Navy", null, 5);

        List<String> violations = validator.validateItem(ProductCategory.SUIT, product, variant, context);

        assertThat(violations).anyMatch(v -> v.contains("formality"));
    }

    @Test
    void validateItem_withFormalityWithinTolerance_isAccepted() {
        var context = contextWithBudget(BigDecimal.valueOf(1000)); // interpretation formality = 8
        var product = RecommendationFixtures.product(ProductCategory.SUIT, 6, BigDecimal.valueOf(400), true); // -2 tolerance
        var variant = RecommendationFixtures.variant(product, "M", "Navy", null, 5);

        assertThat(validator.validateItem(ProductCategory.SUIT, product, variant, context)).isEmpty();
    }

    @Test
    void validateItem_hotWeatherRejectsColdOnlyProduct() {
        var event = RecommendationFixtures.event();
        var preferences = RecommendationFixtures.preferences(
                event.getId(), BigDecimal.valueOf(1000), "M", "9", PreferredStyle.CLASSIC, List.of(), List.of());
        var interpretation = RecommendationFixtures.interpretation(
                event.getId(), OccasionType.WEDDING, 8, List.of(ProductCategory.SUIT), List.of(), List.of());
        var weather = RecommendationFixtures.availableWeather(event.getId(), 32.0, 5, 5.0);
        var context = RecommendationFixtures.context(event, preferences, interpretation, Optional.of(weather));

        var product = RecommendationFixtures.product(ProductCategory.OUTERWEAR, 7, BigDecimal.valueOf(200), true);
        RecommendationFixtures.tagWeather(product, WeatherTag.COLD);
        var variant = RecommendationFixtures.variant(product, "M", "Navy", null, 5);

        List<String> violations = validator.validateItem(ProductCategory.OUTERWEAR, product, variant, context);

        assertThat(violations).anyMatch(v -> v.contains("cold weather"));
    }

    @Test
    void validateItem_coldWeatherRejectsHotOnlyProduct() {
        var event = RecommendationFixtures.event();
        var preferences = RecommendationFixtures.preferences(
                event.getId(), BigDecimal.valueOf(1000), "M", "9", PreferredStyle.CLASSIC, List.of(), List.of());
        var interpretation = RecommendationFixtures.interpretation(
                event.getId(), OccasionType.WEDDING, 8, List.of(ProductCategory.SUIT), List.of(), List.of());
        var weather = RecommendationFixtures.availableWeather(event.getId(), 0.0, 5, 5.0);
        var context = RecommendationFixtures.context(event, preferences, interpretation, Optional.of(weather));

        var product = RecommendationFixtures.product(ProductCategory.OUTERWEAR, 7, BigDecimal.valueOf(200), true);
        RecommendationFixtures.tagWeather(product, WeatherTag.HOT);
        var variant = RecommendationFixtures.variant(product, "M", "Navy", null, 5);

        List<String> violations = validator.validateItem(ProductCategory.OUTERWEAR, product, variant, context);

        assertThat(violations).anyMatch(v -> v.contains("hot weather"));
    }

    @Test
    void validateItem_rainRejectsHotOnlyFootwear() {
        var event = RecommendationFixtures.event();
        var preferences = RecommendationFixtures.preferences(
                event.getId(), BigDecimal.valueOf(1000), "M", "9", PreferredStyle.CLASSIC, List.of(), List.of());
        var interpretation = RecommendationFixtures.interpretation(
                event.getId(), OccasionType.WEDDING, 8, List.of(ProductCategory.SUIT), List.of(), List.of());
        var weather = RecommendationFixtures.availableWeather(event.getId(), 18.0, 80, 5.0);
        var context = RecommendationFixtures.context(event, preferences, interpretation, Optional.of(weather));

        var product = RecommendationFixtures.product(ProductCategory.SHOES, 6, BigDecimal.valueOf(120), true);
        RecommendationFixtures.tagWeather(product, WeatherTag.HOT);
        var variant = RecommendationFixtures.variant(product, "9", "Navy", null, 5);

        List<String> violations = validator.validateItem(ProductCategory.SHOES, product, variant, context);

        assertThat(violations).anyMatch(v -> v.contains("rain is expected"));
    }

    @Test
    void validateItem_missingWeatherNeverFabricatesAConstraint() {
        var context = contextWithBudget(BigDecimal.valueOf(1000)); // no weather snapshot at all
        var product = RecommendationFixtures.product(ProductCategory.OUTERWEAR, 7, BigDecimal.valueOf(200), true);
        RecommendationFixtures.tagWeather(product, WeatherTag.HOT); // would conflict with cold, if it applied
        var variant = RecommendationFixtures.variant(product, "M", "Navy", null, 5);

        assertThat(validator.validateItem(ProductCategory.OUTERWEAR, product, variant, context)).isEmpty();
    }

    @Test
    void validateItem_productWithNoWeatherTagsIsNeverRejectedOnWeatherGrounds() {
        var event = RecommendationFixtures.event();
        var preferences = RecommendationFixtures.preferences(
                event.getId(), BigDecimal.valueOf(1000), "M", "9", PreferredStyle.CLASSIC, List.of(), List.of());
        var interpretation = RecommendationFixtures.interpretation(
                event.getId(), OccasionType.WEDDING, 8, List.of(ProductCategory.SUIT), List.of(), List.of());
        var weather = RecommendationFixtures.availableWeather(event.getId(), 0.0, 5, 5.0);
        var context = RecommendationFixtures.context(event, preferences, interpretation, Optional.of(weather));

        var product = RecommendationFixtures.product(ProductCategory.OUTERWEAR, 7, BigDecimal.valueOf(200), true);
        var variant = RecommendationFixtures.variant(product, "M", "Navy", null, 5);

        assertThat(validator.validateItem(ProductCategory.OUTERWEAR, product, variant, context)).isEmpty();
    }

    @Test
    void validateOutfit_withTotalOverBudget_isRejected() {
        var context = contextWithBudget(BigDecimal.valueOf(100));
        var product = RecommendationFixtures.product(ProductCategory.SUIT, 8, BigDecimal.valueOf(400), true);
        var variant = RecommendationFixtures.variant(product, "M", "Navy", null, 5);
        var candidate = new OutfitCandidate("TEST", List.of(new SelectedItem(
                ProductCategory.SUIT, new EligibleCandidate(product, variant, variant.getEffectivePrice()))));

        List<String> violations = validator.validateOutfit(candidate, context);

        assertThat(violations).anyMatch(v -> v.contains("exceeds budget"));
    }

    @Test
    void validateOutfit_withDuplicateProduct_isRejected() {
        var context = contextWithBudget(BigDecimal.valueOf(1000));
        var product = RecommendationFixtures.product(ProductCategory.SUIT, 8, BigDecimal.valueOf(200), true);
        var variant1 = RecommendationFixtures.variant(product, "M", "Navy", null, 5);
        var candidate = new OutfitCandidate("TEST", List.of(
                new SelectedItem(ProductCategory.SUIT, new EligibleCandidate(product, variant1, variant1.getEffectivePrice())),
                new SelectedItem(ProductCategory.SHOES, new EligibleCandidate(product, variant1, variant1.getEffectivePrice()))));

        List<String> violations = validator.validateOutfit(candidate, context);

        assertThat(violations).anyMatch(v -> v.contains("selected more than once"));
    }

    @Test
    void validateOutfit_withMissingRequiredCategory_isRejected() {
        var context = contextWithBudget(BigDecimal.valueOf(1000)); // requires SUIT + SHOES
        var product = RecommendationFixtures.product(ProductCategory.SHOES, 8, BigDecimal.valueOf(150), true);
        var variant = RecommendationFixtures.variant(product, "9", "Navy", null, 5);
        var candidate = new OutfitCandidate("TEST", List.of(
                new SelectedItem(ProductCategory.SHOES, new EligibleCandidate(product, variant, variant.getEffectivePrice()))));

        List<String> violations = validator.validateOutfit(candidate, context);

        assertThat(violations).anyMatch(v -> v.contains("SUIT") && v.contains("missing"));
    }

    @Test
    void validateOutfit_withEveryConstraintSatisfied_isAccepted() {
        var context = contextWithBudget(BigDecimal.valueOf(1000));
        var suit = RecommendationFixtures.product(ProductCategory.SUIT, 8, BigDecimal.valueOf(400), true);
        var suitVariant = RecommendationFixtures.variant(suit, "M", "Navy", null, 5);
        var shoes = RecommendationFixtures.product(ProductCategory.SHOES, 8, BigDecimal.valueOf(150), true);
        var shoesVariant = RecommendationFixtures.variant(shoes, "9", "Navy", null, 5);
        var candidate = new OutfitCandidate("TEST", List.of(
                new SelectedItem(ProductCategory.SUIT, new EligibleCandidate(suit, suitVariant, suitVariant.getEffectivePrice())),
                new SelectedItem(ProductCategory.SHOES, new EligibleCandidate(shoes, shoesVariant, shoesVariant.getEffectivePrice()))));

        assertThat(validator.validateOutfit(candidate, context)).isEmpty();
    }
}
