package com.stylecast.recommendation;

import com.stylecast.catalog.OccasionTag;
import com.stylecast.catalog.ProductCategory;
import com.stylecast.catalog.StyleTag;
import com.stylecast.catalog.WeatherTag;
import com.stylecast.event.styling.PreferredStyle;
import com.stylecast.occasion.OccasionType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Computes deterministic, bounded (0-100) soft scores for one {@link
 * OutfitCandidate}. Every formula below is the complete, documented
 * definition - there is no randomness and no external (AI/live-provider)
 * input, so the same candidate + context always produces the same scores.
 *
 * <p>{@code overallScore} is a fixed weighted average of the six component
 * scores:
 * <pre>
 * overall = occasionFit * 0.25 + weatherFit * 0.20 + styleFit * 0.15
 *         + colorFit * 0.15 + budgetEfficiency * 0.15 + completeness * 0.10
 * </pre>
 */
@Component
class OutfitScorer {

    private static final Map<OccasionType, OccasionTag> OCCASION_TAG_MAPPING = buildOccasionTagMapping();

    private static final BigDecimal OCCASION_WEIGHT = BigDecimal.valueOf(0.25);
    private static final BigDecimal WEATHER_WEIGHT = BigDecimal.valueOf(0.20);
    private static final BigDecimal STYLE_WEIGHT = BigDecimal.valueOf(0.15);
    private static final BigDecimal COLOR_WEIGHT = BigDecimal.valueOf(0.15);
    private static final BigDecimal BUDGET_WEIGHT = BigDecimal.valueOf(0.15);
    private static final BigDecimal COMPLETENESS_WEIGHT = BigDecimal.valueOf(0.10);

    /** Neutral score used whenever the relevant preference/data simply isn't present - never 0. */
    private static final int NEUTRAL_SCORE = 70;

    OutfitScore score(OutfitCandidate candidate, RecommendationContext context) {
        int occasionFit = occasionFitScore(candidate, context);
        int weatherFit = weatherFitScore(candidate, context);
        int styleFit = styleFitScore(candidate, context);
        int colorFit = colorFitScore(candidate, context);
        int budgetEfficiency = budgetEfficiencyScore(candidate, context);
        int completeness = completenessScore(candidate, context);

        BigDecimal overall = BigDecimal.valueOf(occasionFit).multiply(OCCASION_WEIGHT)
                .add(BigDecimal.valueOf(weatherFit).multiply(WEATHER_WEIGHT))
                .add(BigDecimal.valueOf(styleFit).multiply(STYLE_WEIGHT))
                .add(BigDecimal.valueOf(colorFit).multiply(COLOR_WEIGHT))
                .add(BigDecimal.valueOf(budgetEfficiency).multiply(BUDGET_WEIGHT))
                .add(BigDecimal.valueOf(completeness).multiply(COMPLETENESS_WEIGHT));

        return new OutfitScore(
                occasionFit, weatherFit, styleFit, colorFit, budgetEfficiency, completeness,
                clamp(overall.setScale(0, RoundingMode.HALF_UP).intValue()));
    }

    /** {@code 100 * (1 - avgFormalityDiff/10) * (0.5 + 0.5 * occasionTagMatchFraction)}. */
    private int occasionFitScore(OutfitCandidate candidate, RecommendationContext context) {
        List<SelectedItem> items = candidate.items();
        if (items.isEmpty()) {
            return NEUTRAL_SCORE;
        }

        double avgFormalityDiff = items.stream()
                .mapToInt(item -> Math.min(10, Math.abs(item.candidate().product().getFormalityLevel() - context.formalityLevel())))
                .average()
                .orElse(0);

        OccasionTag mappedTag = OCCASION_TAG_MAPPING.get(context.occasion());
        long matched = mappedTag == null ? 0 : items.stream()
                .filter(item -> item.candidate().product().getOccasionTags().contains(mappedTag))
                .count();
        double tagMatchFraction = (double) matched / items.size();

        double score = 100 * (1 - avgFormalityDiff / 10) * (0.5 + 0.5 * tagMatchFraction);
        return clamp((int) Math.round(score));
    }

    /** Neutral (70) whenever weather data is unavailable; otherwise an average of per-item weather alignment. */
    private int weatherFitScore(OutfitCandidate candidate, RecommendationContext context) {
        RecommendationContext.WeatherSignal signal = context.weatherSignal();
        if (!signal.available() || candidate.items().isEmpty()) {
            return NEUTRAL_SCORE;
        }

        WeatherTag dominant = signal.hot() ? WeatherTag.HOT : signal.cold() ? WeatherTag.COLD : WeatherTag.MILD;

        double total = candidate.items().stream()
                .mapToInt(item -> itemWeatherScore(item, dominant, signal))
                .average()
                .orElse(NEUTRAL_SCORE);
        return clamp((int) Math.round(total));
    }

    private int itemWeatherScore(SelectedItem item, WeatherTag dominant, RecommendationContext.WeatherSignal signal) {
        var tags = item.candidate().product().getWeatherTags();
        int score = tags.contains(dominant) ? 100 : NEUTRAL_SCORE;
        if (signal.rainy() && (item.category() == ProductCategory.SHOES || item.category() == ProductCategory.OUTERWEAR)
                && tags.contains(WeatherTag.RAIN)) {
            score = Math.min(100, score + 15);
        }
        if (signal.windy() && item.category() == ProductCategory.OUTERWEAR && tags.contains(WeatherTag.WIND)) {
            score = Math.min(100, score + 10);
        }
        return score;
    }

    /** {@code round(40 + 60 * matchFraction)} against the user's preferred style. */
    private int styleFitScore(OutfitCandidate candidate, RecommendationContext context) {
        List<SelectedItem> items = candidate.items();
        if (items.isEmpty()) {
            return NEUTRAL_SCORE;
        }
        PreferredStyle preferredStyle = context.preferredStyle();
        StyleTag mapped = StyleTag.valueOf(preferredStyle.name());

        long matched = items.stream().filter(item -> item.candidate().product().getStyleTags().contains(mapped)).count();
        double fraction = (double) matched / items.size();
        return clamp((int) Math.round(40 + 60 * fraction));
    }

    /** Neutral (70) when the user set no color preference; otherwise {@code round(50 + 50 * matchFraction)}. */
    private int colorFitScore(OutfitCandidate candidate, RecommendationContext context) {
        var preferredColors = context.preferredColors();
        List<SelectedItem> items = candidate.items();
        if (preferredColors.isEmpty() || items.isEmpty()) {
            return NEUTRAL_SCORE;
        }
        long matched = items.stream()
                .filter(item -> preferredColors.contains(item.candidate().variant().getColor().toLowerCase(Locale.ROOT)))
                .count();
        double fraction = (double) matched / items.size();
        return clamp((int) Math.round(50 + 50 * fraction));
    }

    /** {@code round(100 * min(1, totalPrice / maxBudget))} - rewards using the available budget well. */
    private int budgetEfficiencyScore(OutfitCandidate candidate, RecommendationContext context) {
        BigDecimal maxBudget = context.maxBudget();
        if (maxBudget.signum() <= 0) {
            return NEUTRAL_SCORE;
        }
        BigDecimal ratio = candidate.totalPrice().divide(maxBudget, 4, RoundingMode.HALF_UP);
        BigDecimal bounded = ratio.min(BigDecimal.ONE);
        return clamp(bounded.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).intValue());
    }

    /** {@code round(100 * (requiredCount + optionalIncluded) / (requiredCount + optionalTotal))}. */
    private int completenessScore(OutfitCandidate candidate, RecommendationContext context) {
        int requiredCount = context.requiredCategories().size();
        List<ProductCategory> optionalCategories = context.optionalCategories();
        if (optionalCategories.isEmpty()) {
            return 100;
        }
        Map<ProductCategory, Boolean> present = new EnumMap<>(ProductCategory.class);
        candidate.items().forEach(item -> present.put(item.category(), true));
        long optionalIncluded = optionalCategories.stream().filter(present::containsKey).count();

        double score = 100.0 * (requiredCount + optionalIncluded) / (requiredCount + optionalCategories.size());
        return clamp((int) Math.round(score));
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private static Map<OccasionType, OccasionTag> buildOccasionTagMapping() {
        Map<OccasionType, OccasionTag> mapping = new EnumMap<>(OccasionType.class);
        mapping.put(OccasionType.WEDDING, OccasionTag.WEDDING);
        mapping.put(OccasionType.INTERVIEW, OccasionTag.INTERVIEW);
        mapping.put(OccasionType.BUSINESS_MEETING, OccasionTag.NETWORKING);
        mapping.put(OccasionType.NETWORKING, OccasionTag.NETWORKING);
        mapping.put(OccasionType.CONFERENCE, OccasionTag.NETWORKING);
        mapping.put(OccasionType.DINNER, OccasionTag.DINNER);
        mapping.put(OccasionType.DATE, OccasionTag.DINNER);
        mapping.put(OccasionType.CONCERT, OccasionTag.CONCERT);
        mapping.put(OccasionType.PARTY, OccasionTag.CONCERT);
        mapping.put(OccasionType.CASUAL_OUTING, OccasionTag.CASUAL);
        mapping.put(OccasionType.FORMAL_EVENT, OccasionTag.FORMAL_EVENT);
        // OccasionType.UNKNOWN intentionally has no mapping: occasion-tag matching
        // contributes nothing (not a fabricated match) when the occasion itself is unknown.
        return mapping;
    }
}
