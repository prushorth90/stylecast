package com.stylecast.recommendation;

import com.stylecast.catalog.ProductCategory;
import com.stylecast.event.styling.ShoppingDepartment;
import com.stylecast.retail.RetailProductSearchRequest;
import com.stylecast.retail.Retailer;
import com.stylecast.retail.TargetAudience;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds one targeted, structured {@link RetailProductSearchRequest} per
 * required garment category for a live-Nordstrom recommendation attempt.
 *
 * <p>The user never constructs a Nordstrom search themselves - every field
 * here comes automatically from the event's already-loaded {@link
 * RecommendationContext} (saved styling preferences and occasion
 * interpretation), never from free-form user input at request time.
 */
@Component
class LiveCategorySearchRequestFactory {

    /** Small, bounded candidate pool per category - enough to assemble up to three distinct outfits. */
    static final int CANDIDATES_PER_CATEGORY = 3;

    List<RetailProductSearchRequest> buildRequests(RecommendationContext context, List<ProductCategory> requiredCategories) {
        BigDecimal perCategoryBudget = allocateBudgetPerCategory(context.maxBudget(), requiredCategories.size());
        TargetAudience targetAudience = toTargetAudience(context.shoppingDepartment());

        List<RetailProductSearchRequest> requests = new ArrayList<>();
        for (ProductCategory category : requiredCategories) {
            requests.add(new RetailProductSearchRequest(
                    Retailer.NORDSTROM,
                    category,
                    buildKeywords(context, targetAudience, category),
                    perCategoryBudget,
                    sizeFor(category, context),
                    targetAudience,
                    CANDIDATES_PER_CATEGORY));
        }
        return requests;
    }

    /**
     * Maps the user's saved {@code shoppingDepartment} styling preference
     * directly to a {@link TargetAudience} - the sole source of the live
     * search's department constraint (never inferred from the required
     * categories).
     */
    private TargetAudience toTargetAudience(ShoppingDepartment shoppingDepartment) {
        return switch (shoppingDepartment) {
            case MEN -> TargetAudience.MEN;
            case WOMEN -> TargetAudience.WOMEN;
            case UNISEX -> TargetAudience.UNISEX;
            case NO_PREFERENCE -> TargetAudience.NO_PREFERENCE;
        };
    }

    /**
     * Splits the event's total budget evenly across every required category
     * as a soft "maximum price" hint on each search (the live provider never
     * independently confirms price, so this can only bias the search, not
     * strictly enforce a budget - see Task 8 notes).
     */
    private BigDecimal allocateBudgetPerCategory(BigDecimal totalBudget, int categoryCount) {
        if (categoryCount <= 0) {
            return totalBudget;
        }
        return totalBudget.divide(BigDecimal.valueOf(categoryCount), 2, RoundingMode.DOWN);
    }

    private List<String> buildKeywords(RecommendationContext context, TargetAudience targetAudience, ProductCategory category) {
        List<String> keywords = new ArrayList<>();
        keywords.addAll(CategorySynonyms.synonymsFor(category));
        context.preferredColors().stream().limit(2).forEach(keywords::add);
        keywords.add(context.preferredStyle().name().toLowerCase(Locale.ROOT));
        keywords.add(context.occasion().name().toLowerCase(Locale.ROOT).replace('_', ' '));
        if (context.interpretation().getDressCode() != null) {
            keywords.add(context.interpretation().getDressCode().name().toLowerCase(Locale.ROOT).replace('_', ' '));
        }
        switch (targetAudience) {
            case MEN -> {
                keywords.add("men's");
                keywords.add("mens");
            }
            case WOMEN -> {
                keywords.add("women's");
                keywords.add("womens");
            }
            case UNISEX -> {
                keywords.add("unisex");
                keywords.add("gender-neutral");
            }
            case NO_PREFERENCE -> {
                // No department keyword added - the search is intentionally unrestricted.
            }
        }
        return keywords;
    }

    /** No size hint for accessories (one-size-fits-all, same rule as the local catalog engine). */
    private String sizeFor(ProductCategory category, RecommendationContext context) {
        if (category == ProductCategory.ACCESSORY) {
            return null;
        }
        return category == ProductCategory.SHOES ? context.shoeSize() : context.clothingSize();
    }
}
