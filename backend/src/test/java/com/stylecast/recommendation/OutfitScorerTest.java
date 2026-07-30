package com.stylecast.recommendation;

import com.stylecast.catalog.OccasionTag;
import com.stylecast.catalog.ProductCategory;
import com.stylecast.catalog.StyleTag;
import com.stylecast.catalog.WeatherTag;
import com.stylecast.event.styling.PreferredStyle;
import com.stylecast.occasion.OccasionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OutfitScorer}'s six deterministic soft-scoring
 * components and the weighted overall score.
 */
class OutfitScorerTest {

    private final OutfitScorer scorer = new OutfitScorer();

    @Test
    void score_isFullyDeterministic_sameInputProducesSameOutput() {
        var event = RecommendationFixtures.event();
        var preferences = RecommendationFixtures.preferences(
                event.getId(), BigDecimal.valueOf(1000), "M", "9", PreferredStyle.CLASSIC, List.of("navy"), List.of());
        var interpretation = RecommendationFixtures.interpretation(
                event.getId(), OccasionType.WEDDING, 8, List.of(ProductCategory.SUIT), List.of(ProductCategory.ACCESSORY), List.of());
        var context = RecommendationFixtures.context(event, preferences, interpretation, Optional.empty());

        var suit = RecommendationFixtures.product(ProductCategory.SUIT, 8, BigDecimal.valueOf(400), true);
        RecommendationFixtures.tagOccasion(suit, OccasionTag.WEDDING);
        RecommendationFixtures.tagStyle(suit, StyleTag.CLASSIC);
        var variant = RecommendationFixtures.variant(suit, "M", "Navy", null, 5);
        var candidate = new OutfitCandidate("FORMAL_MENSWEAR", List.of(
                new SelectedItem(ProductCategory.SUIT, new EligibleCandidate(suit, variant, variant.getEffectivePrice()))));

        OutfitScore first = scorer.score(candidate, context);
        OutfitScore second = scorer.score(candidate, context);

        assertThat(second).isEqualTo(first);
    }

    @Test
    void score_everyComponentIsBoundedZeroToHundred() {
        var event = RecommendationFixtures.event();
        var preferences = RecommendationFixtures.preferences(
                event.getId(), BigDecimal.valueOf(1000), "M", "9", PreferredStyle.BOLD, List.of(), List.of());
        var interpretation = RecommendationFixtures.interpretation(
                event.getId(), OccasionType.UNKNOWN, 2, List.of(ProductCategory.SHIRT), List.of(), List.of());
        var context = RecommendationFixtures.context(event, preferences, interpretation, Optional.empty());

        // Deliberately mismatched formality/style/occasion so every component is exercised at its low end.
        var shirt = RecommendationFixtures.product(ProductCategory.SHIRT, 9, BigDecimal.valueOf(400), true);
        var variant = RecommendationFixtures.variant(shirt, "M", "Navy", null, 5);
        var candidate = new OutfitCandidate("SMART_CASUAL", List.of(
                new SelectedItem(ProductCategory.SHIRT, new EligibleCandidate(shirt, variant, variant.getEffectivePrice()))));

        OutfitScore score = scorer.score(candidate, context);

        assertThat(score.occasionFitScore()).isBetween(0, 100);
        assertThat(score.weatherFitScore()).isBetween(0, 100);
        assertThat(score.styleFitScore()).isBetween(0, 100);
        assertThat(score.colorFitScore()).isBetween(0, 100);
        assertThat(score.budgetEfficiencyScore()).isBetween(0, 100);
        assertThat(score.completenessScore()).isBetween(0, 100);
        assertThat(score.overallScore()).isBetween(0, 100);
    }

    @Test
    void weatherFitScore_whenWeatherUnavailable_isNeutralNotZero() {
        var event = RecommendationFixtures.event();
        var preferences = RecommendationFixtures.preferences(
                event.getId(), BigDecimal.valueOf(1000), "M", "9", PreferredStyle.CLASSIC, List.of(), List.of());
        var interpretation = RecommendationFixtures.interpretation(
                event.getId(), OccasionType.WEDDING, 8, List.of(ProductCategory.SUIT), List.of(), List.of());
        var context = RecommendationFixtures.context(event, preferences, interpretation, Optional.empty());

        var suit = RecommendationFixtures.product(ProductCategory.SUIT, 8, BigDecimal.valueOf(400), true);
        var variant = RecommendationFixtures.variant(suit, "M", "Navy", null, 5);
        var candidate = new OutfitCandidate("FORMAL_MENSWEAR", List.of(
                new SelectedItem(ProductCategory.SUIT, new EligibleCandidate(suit, variant, variant.getEffectivePrice()))));

        OutfitScore score = scorer.score(candidate, context);

        assertThat(score.weatherFitScore()).isEqualTo(70);
    }

    @Test
    void weatherFitScore_whenTagsMatchDominantCondition_scoresHigherThanUnrelatedTags() {
        var event = RecommendationFixtures.event();
        var preferences = RecommendationFixtures.preferences(
                event.getId(), BigDecimal.valueOf(1000), "M", "9", PreferredStyle.CLASSIC, List.of(), List.of());
        var interpretation = RecommendationFixtures.interpretation(
                event.getId(), OccasionType.WEDDING, 8, List.of(ProductCategory.OUTERWEAR), List.of(), List.of());
        var weather = RecommendationFixtures.availableWeather(event.getId(), 0.0, 10, 5.0);
        var context = RecommendationFixtures.context(event, preferences, interpretation, Optional.of(weather));

        var coldCoat = RecommendationFixtures.product(ProductCategory.OUTERWEAR, 7, BigDecimal.valueOf(200), true);
        RecommendationFixtures.tagWeather(coldCoat, WeatherTag.COLD);
        var coldVariant = RecommendationFixtures.variant(coldCoat, "M", "Navy", null, 5);
        var coldCandidate = new OutfitCandidate("FORMAL_MENSWEAR", List.of(
                new SelectedItem(ProductCategory.OUTERWEAR, new EligibleCandidate(coldCoat, coldVariant, coldVariant.getEffectivePrice()))));

        var neutralCoat = RecommendationFixtures.product(ProductCategory.OUTERWEAR, 7, BigDecimal.valueOf(200), true);
        var neutralVariant = RecommendationFixtures.variant(neutralCoat, "M", "Navy", null, 5);
        var neutralCandidate = new OutfitCandidate("FORMAL_MENSWEAR", List.of(
                new SelectedItem(ProductCategory.OUTERWEAR, new EligibleCandidate(neutralCoat, neutralVariant, neutralVariant.getEffectivePrice()))));

        assertThat(scorer.score(coldCandidate, context).weatherFitScore())
                .isGreaterThan(scorer.score(neutralCandidate, context).weatherFitScore());
    }

    @Test
    void colorFitScore_whenNoPreferredColorsSet_isNeutralNotZero() {
        var event = RecommendationFixtures.event();
        var preferences = RecommendationFixtures.preferences(
                event.getId(), BigDecimal.valueOf(1000), "M", "9", PreferredStyle.CLASSIC, List.of(), List.of());
        var interpretation = RecommendationFixtures.interpretation(
                event.getId(), OccasionType.WEDDING, 8, List.of(ProductCategory.SUIT), List.of(), List.of());
        var context = RecommendationFixtures.context(event, preferences, interpretation, Optional.empty());

        var suit = RecommendationFixtures.product(ProductCategory.SUIT, 8, BigDecimal.valueOf(400), true);
        var variant = RecommendationFixtures.variant(suit, "M", "Purple", null, 5);
        var candidate = new OutfitCandidate("FORMAL_MENSWEAR", List.of(
                new SelectedItem(ProductCategory.SUIT, new EligibleCandidate(suit, variant, variant.getEffectivePrice()))));

        assertThat(scorer.score(candidate, context).colorFitScore()).isEqualTo(70);
    }

    @Test
    void budgetEfficiencyScore_usingWholeBudgetScoresHigherThanUsingLittleOfIt() {
        var event = RecommendationFixtures.event();
        var preferences = RecommendationFixtures.preferences(
                event.getId(), BigDecimal.valueOf(1000), "M", "9", PreferredStyle.CLASSIC, List.of(), List.of());
        var interpretation = RecommendationFixtures.interpretation(
                event.getId(), OccasionType.WEDDING, 8, List.of(ProductCategory.SUIT), List.of(), List.of());
        var context = RecommendationFixtures.context(event, preferences, interpretation, Optional.empty());

        var expensiveSuit = RecommendationFixtures.product(ProductCategory.SUIT, 8, BigDecimal.valueOf(950), true);
        var expensiveVariant = RecommendationFixtures.variant(expensiveSuit, "M", "Navy", null, 5);
        var expensiveCandidate = new OutfitCandidate("FORMAL_MENSWEAR", List.of(
                new SelectedItem(ProductCategory.SUIT, new EligibleCandidate(expensiveSuit, expensiveVariant, expensiveVariant.getEffectivePrice()))));

        var cheapSuit = RecommendationFixtures.product(ProductCategory.SUIT, 8, BigDecimal.valueOf(50), true);
        var cheapVariant = RecommendationFixtures.variant(cheapSuit, "M", "Navy", null, 5);
        var cheapCandidate = new OutfitCandidate("FORMAL_MENSWEAR", List.of(
                new SelectedItem(ProductCategory.SUIT, new EligibleCandidate(cheapSuit, cheapVariant, cheapVariant.getEffectivePrice()))));

        assertThat(scorer.score(expensiveCandidate, context).budgetEfficiencyScore())
                .isGreaterThan(scorer.score(cheapCandidate, context).budgetEfficiencyScore());
    }

    @Test
    void completenessScore_includingOptionalCategoryScoresHigherThanWithoutIt() {
        var event = RecommendationFixtures.event();
        var preferences = RecommendationFixtures.preferences(
                event.getId(), BigDecimal.valueOf(1000), "M", "9", PreferredStyle.CLASSIC, List.of(), List.of());
        var interpretation = RecommendationFixtures.interpretation(
                event.getId(), OccasionType.WEDDING, 8, List.of(ProductCategory.SUIT), List.of(ProductCategory.ACCESSORY), List.of());
        var context = RecommendationFixtures.context(event, preferences, interpretation, Optional.empty());

        var suit = RecommendationFixtures.product(ProductCategory.SUIT, 8, BigDecimal.valueOf(400), true);
        var suitVariant = RecommendationFixtures.variant(suit, "M", "Navy", null, 5);
        var accessory = RecommendationFixtures.product(ProductCategory.ACCESSORY, 8, BigDecimal.valueOf(40), true);
        var accessoryVariant = RecommendationFixtures.variant(accessory, "ONE_SIZE", "Navy", null, 5);

        var withoutAccessory = new OutfitCandidate("FORMAL_MENSWEAR", List.of(
                new SelectedItem(ProductCategory.SUIT, new EligibleCandidate(suit, suitVariant, suitVariant.getEffectivePrice()))));
        var withAccessory = new OutfitCandidate("FORMAL_MENSWEAR", List.of(
                new SelectedItem(ProductCategory.SUIT, new EligibleCandidate(suit, suitVariant, suitVariant.getEffectivePrice())),
                new SelectedItem(ProductCategory.ACCESSORY, new EligibleCandidate(accessory, accessoryVariant, accessoryVariant.getEffectivePrice()))));

        assertThat(scorer.score(withAccessory, context).completenessScore())
                .isGreaterThan(scorer.score(withoutAccessory, context).completenessScore());
    }
}
