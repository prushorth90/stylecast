package com.stylecast.recommendation;

import com.stylecast.catalog.Product;
import com.stylecast.catalog.ProductCategory;
import com.stylecast.catalog.ProductVariant;
import com.stylecast.catalog.WeatherTag;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The single source of truth for every hard constraint listed in the Task
 * 7A spec. Used two ways:
 *
 * <ul>
 *   <li>{@link #validateItem} checks one product+variant candidate on its
 *       own (active, size, stock, color, formality, weather) - {@link
 *       ProductEligibilityService} uses this to shrink the search space.</li>
 *   <li>{@link #validateOutfit} checks a fully-assembled {@link
 *       OutfitCandidate} as a whole (budget, required categories present,
 *       no duplicate product/variant) - {@link OutfitCombinationGenerator}
 *       uses this as the final gate before a combination is accepted.</li>
 * </ul>
 *
 * <p>Every returned violation list is empty when the input is valid; a
 * non-empty list is always human-readable (used to build "no valid outfit"
 * explanations) and never merely a boolean.
 */
@Component
class HardConstraintValidator {

    /**
     * Formality band: a product's formality level must be within
     * {@code [interpretationFormality - 4, interpretationFormality + 1]}.
     * The lower bound enforces a "minimum practical formality" (nothing too
     * casual for the occasion) while staying wide enough that categories
     * with a naturally lower formality ceiling in this catalog (e.g. dress
     * shirts top out around 6) can still pair with a highly formal main
     * piece (e.g. a formality-9 wedding suit); the upper bound allows only
     * slight over-dressing without requiring an exact match.
     */
    private static final int FORMALITY_TOLERANCE_BELOW = 4;
    private static final int FORMALITY_TOLERANCE_ABOVE = 1;

    /**
     * Categories the outfit templates treat as interchangeable "outer
     * layer" alternatives (see {@link OutfitTemplateCatalog}): a SUIT
     * already includes trousers, so a BLAZER+TROUSERS combination is an
     * equally valid way to satisfy an occasion interpretation that asked
     * for {@code SUIT}, and a SKIRT+top combination is an equally valid
     * way to satisfy one that asked for {@code DRESS}. Used only when
     * checking "required categories must be satisfied" - it never affects
     * per-item filtering.
     */
    private static final Map<ProductCategory, Set<ProductCategory>> SUBSTITUTABLE_CATEGORIES = buildSubstitutionGroups();

    List<String> validateItem(ProductCategory category, Product product, ProductVariant variant, RecommendationContext context) {
        List<String> violations = new ArrayList<>();

        if (!product.isActive()) {
            violations.add("product " + product.getId() + " is not active");
        }
        if (!variant.isInStock()) {
            violations.add("variant " + variant.getId() + " has no in-stock inventory record");
        }
        if (!sizeMatches(category, variant, context)) {
            violations.add("variant " + variant.getId() + " size " + variant.getClothingSize() + " does not match requested size");
        }
        if (isAvoidedColor(variant, context)) {
            violations.add("variant " + variant.getId() + " color " + variant.getColor() + " is an avoided color");
        }
        if (!formalitySuits(product, context)) {
            violations.add("product " + product.getId() + " formality " + product.getFormalityLevel()
                    + " does not suit interpreted formality " + context.formalityLevel());
        }
        String weatherViolation = weatherViolation(category, product, context.weatherSignal());
        if (weatherViolation != null) {
            violations.add(weatherViolation);
        }

        return violations;
    }

    List<String> validateOutfit(OutfitCandidate candidate, RecommendationContext context) {
        List<String> violations = new ArrayList<>();

        BigDecimal total = candidate.totalPrice();
        if (total.compareTo(context.maxBudget()) > 0) {
            violations.add("total price " + total + " exceeds budget " + context.maxBudget());
        }

        Set<ProductCategory> presentCategories = new HashSet<>();
        Set<java.util.UUID> productIds = new HashSet<>();
        Set<java.util.UUID> variantIds = new HashSet<>();
        for (SelectedItem item : candidate.items()) {
            presentCategories.add(item.category());
            var product = item.candidate().product();
            var variant = item.candidate().variant();
            if (!productIds.add(product.getId())) {
                violations.add("product " + product.getId() + " is selected more than once");
            }
            if (!variantIds.add(variant.getId())) {
                violations.add("variant " + variant.getId() + " is selected more than once");
            }
        }

        for (ProductCategory required : context.requiredCategories()) {
            Set<ProductCategory> acceptableCategories = SUBSTITUTABLE_CATEGORIES.getOrDefault(required, Set.of(required));
            boolean satisfied = presentCategories.stream().anyMatch(acceptableCategories::contains);
            if (!satisfied) {
                violations.add("required category " + required + " is missing");
            }
        }

        return violations;
    }

    private boolean sizeMatches(ProductCategory category, ProductVariant variant, RecommendationContext context) {
        if (category == ProductCategory.ACCESSORY) {
            // Accessories are one-size-fits-all in this catalog; no size constraint applies.
            return true;
        }
        String requestedSize = category == ProductCategory.SHOES ? context.shoeSize() : context.clothingSize();
        return variant.getClothingSize().equalsIgnoreCase(requestedSize);
    }

    private boolean isAvoidedColor(ProductVariant variant, RecommendationContext context) {
        return context.colorsToAvoid().contains(variant.getColor().toLowerCase(Locale.ROOT));
    }

    private boolean formalitySuits(Product product, RecommendationContext context) {
        int min = context.formalityLevel() - FORMALITY_TOLERANCE_BELOW;
        int max = context.formalityLevel() + FORMALITY_TOLERANCE_ABOVE;
        return product.getFormalityLevel() >= min && product.getFormalityLevel() <= max;
    }

    /**
     * Returns a violation message when the weather signal (only when
     * {@link RecommendationContext.WeatherSignal#available()}) directly
     * conflicts with a product's weather tags, or {@code null} when there is
     * no conflict (including whenever weather data is unavailable, or the
     * product simply has no weather tags - absence of a tag is never treated
     * as a violation).
     */
    private String weatherViolation(ProductCategory category, Product product, RecommendationContext.WeatherSignal signal) {
        if (!signal.available()) {
            return null;
        }
        Set<WeatherTag> tags = product.getWeatherTags();

        if (signal.hot() && tags.contains(WeatherTag.COLD) && !tags.contains(WeatherTag.HOT) && !tags.contains(WeatherTag.MILD)) {
            return "product " + product.getId() + " is tagged only for cold weather but the forecast is hot";
        }
        if (signal.cold() && tags.contains(WeatherTag.HOT) && !tags.contains(WeatherTag.COLD) && !tags.contains(WeatherTag.MILD)) {
            return "product " + product.getId() + " is tagged only for hot weather but the forecast is cold";
        }
        if (signal.rainy() && category == ProductCategory.SHOES
                && tags.contains(WeatherTag.HOT) && !tags.contains(WeatherTag.RAIN)
                && !tags.contains(WeatherTag.MILD) && !tags.contains(WeatherTag.COLD)) {
            return "footwear " + product.getId() + " is tagged only for hot/dry weather but rain is expected";
        }
        return null;
    }

    private static Map<ProductCategory, Set<ProductCategory>> buildSubstitutionGroups() {
        Map<ProductCategory, Set<ProductCategory>> groups = new EnumMap<>(ProductCategory.class);
        Set<ProductCategory> menswearOuter = Set.of(ProductCategory.SUIT, ProductCategory.BLAZER);
        Set<ProductCategory> dressBased = Set.of(ProductCategory.DRESS, ProductCategory.SKIRT);
        groups.put(ProductCategory.SUIT, menswearOuter);
        groups.put(ProductCategory.BLAZER, menswearOuter);
        groups.put(ProductCategory.DRESS, dressBased);
        groups.put(ProductCategory.SKIRT, dressBased);
        return groups;
    }
}
