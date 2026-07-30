package com.stylecast.recommendation;

import com.stylecast.catalog.ProductCategory;
import com.stylecast.event.Event;
import com.stylecast.event.styling.EventStylePreferences;
import com.stylecast.event.styling.PreferredStyle;
import com.stylecast.event.styling.ShoppingDepartment;
import com.stylecast.occasion.OccasionInterpretation;
import com.stylecast.occasion.OccasionType;
import com.stylecast.occasion.RequestedItem;
import com.stylecast.weather.EventWeatherSnapshot;
import com.stylecast.weather.WeatherAvailabilityStatus;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Everything the recommendation engine needs for one event, bundled by
 * {@link RecommendationContextLoader}. Bundles the required inputs listed in
 * docs/ARCHITECTURE.md's recommendation module: the event, its saved styling
 * preferences, its occasion interpretation, and its latest weather snapshot
 * (optional - weather may not be available yet).
 */
public record RecommendationContext(
        Event event,
        EventStylePreferences preferences,
        OccasionInterpretation interpretation,
        Optional<EventWeatherSnapshot> weather) {

    /** Hot/cold/rain/wind signal derived from the weather snapshot, if any. */
    private static final BigDecimal HOT_THRESHOLD_CELSIUS = BigDecimal.valueOf(27);
    private static final BigDecimal COLD_THRESHOLD_CELSIUS = BigDecimal.valueOf(12);
    private static final int RAIN_PROBABILITY_THRESHOLD_PERCENT = 50;
    private static final BigDecimal WIND_THRESHOLD_KMH = BigDecimal.valueOf(25);

    public BigDecimal maxBudget() {
        return preferences.getMaxBudget();
    }

    public String clothingSize() {
        return preferences.getClothingSize();
    }

    public String shoeSize() {
        return preferences.getShoeSize();
    }

    public PreferredStyle preferredStyle() {
        return preferences.getPreferredStyle();
    }

    public ShoppingDepartment shoppingDepartment() {
        return preferences.getShoppingDepartment();
    }

    public int formalityLevel() {
        return interpretation.getFormalityLevel();
    }

    public OccasionType occasion() {
        return interpretation.getOccasion();
    }

    /**
     * Colors to exclude (hard constraint), normalized to lowercase: the
     * union of the user's saved "colors to avoid" and the occasion
     * interpretation's "colors to avoid".
     */
    public Set<String> colorsToAvoid() {
        Set<String> colors = new LinkedHashSet<>();
        preferences.getColorsToAvoid().forEach(c -> colors.add(c.toLowerCase(Locale.ROOT)));
        interpretation.getColorsToAvoid().forEach(c -> colors.add(c.toLowerCase(Locale.ROOT)));
        return colors;
    }

    /** Preferred colors (soft scoring only), normalized to lowercase. */
    public Set<String> preferredColors() {
        Set<String> colors = new LinkedHashSet<>();
        preferences.getPreferredColors().forEach(c -> colors.add(c.toLowerCase(Locale.ROOT)));
        interpretation.getPreferredColors().forEach(c -> colors.add(c.toLowerCase(Locale.ROOT)));
        return colors;
    }

    /**
     * Required categories for a complete outfit: the occasion
     * interpretation's required categories, plus {@code SHOES} always
     * (an outfit isn't complete without footwear, even if the classifier
     * didn't explicitly list it).
     */
    public List<ProductCategory> requiredCategories() {
        Set<ProductCategory> required = new LinkedHashSet<>(interpretation.getRequiredCategories());
        required.add(ProductCategory.SHOES);
        return List.copyOf(required);
    }

    /** Optional categories, excluding anything already required. */
    public List<ProductCategory> optionalCategories() {
        Set<ProductCategory> required = new LinkedHashSet<>(requiredCategories());
        return interpretation.getOptionalCategories().stream()
                .filter(category -> !required.contains(category))
                .distinct()
                .toList();
    }

    /**
     * Explicit product phrases extracted from the user's saved outfit
     * request (Task 8.5) - never {@code null}, empty for events where the
     * interpretation found no specific product phrases, or for
     * interpretations generated before Task 8.5 existed. Live-search
     * generation must prefer these over {@link #requiredCategories()}
     * whenever non-empty; see {@code LiveRecommendationService}.
     */
    public List<RequestedItem> requestedItems() {
        return interpretation.getRequestedItems();
    }

    /**
     * Derives the event's weather signal used by hard constraints and soft
     * scoring. Returns {@link WeatherSignal#unavailable()} whenever there is
     * no snapshot, or the snapshot's forecast is unavailable - callers must
     * never fabricate a hot/cold/rain/wind condition in that case.
     */
    public WeatherSignal weatherSignal() {
        return weather
                .filter(snapshot -> snapshot.getStatus() == WeatherAvailabilityStatus.AVAILABLE)
                .map(this::deriveSignal)
                .orElseGet(WeatherSignal::unavailable);
    }

    private WeatherSignal deriveSignal(EventWeatherSnapshot snapshot) {
        BigDecimal start = snapshot.getTemperatureAtStart();
        BigDecimal end = snapshot.getTemperatureAtEnd();
        boolean hot = (start != null && start.compareTo(HOT_THRESHOLD_CELSIUS) > 0)
                || (end != null && end.compareTo(HOT_THRESHOLD_CELSIUS) > 0);
        boolean cold = (start != null && start.compareTo(COLD_THRESHOLD_CELSIUS) < 0)
                || (end != null && end.compareTo(COLD_THRESHOLD_CELSIUS) < 0);
        boolean rainy = snapshot.getPrecipitationProbability() != null
                && snapshot.getPrecipitationProbability() >= RAIN_PROBABILITY_THRESHOLD_PERCENT;
        boolean windy = snapshot.getWindSpeed() != null
                && snapshot.getWindSpeed().compareTo(WIND_THRESHOLD_KMH) >= 0;
        // Hot and cold are mutually exclusive by construction of the two
        // thresholds (27 > 12), so at most one of them is ever true.
        return new WeatherSignal(true, hot, cold, rainy, windy);
    }

    /**
     * A derived, boolean weather signal. {@code available=false} means no
     * hard weather constraint or soft weather score should be applied -
     * missing weather must not be treated as an implicit "anything goes" or
     * as a fabricated extreme condition.
     */
    public record WeatherSignal(boolean available, boolean hot, boolean cold, boolean rainy, boolean windy) {
        public static WeatherSignal unavailable() {
            return new WeatherSignal(false, false, false, false, false);
        }
    }
}
