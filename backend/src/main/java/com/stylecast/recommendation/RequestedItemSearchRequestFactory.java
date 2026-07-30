package com.stylecast.recommendation;

import com.stylecast.event.styling.ShoppingDepartment;
import com.stylecast.occasion.GenericItemCategory;
import com.stylecast.occasion.RequestedItem;
import com.stylecast.retail.RetailProductSearchRequest;
import com.stylecast.retail.Retailer;
import com.stylecast.retail.TargetAudience;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Builds one targeted {@link RetailProductSearchRequest} per explicit
 * {@link RequestedItem} - the live-search counterpart of {@link
 * LiveCategorySearchRequestFactory}, used instead of it whenever an event's
 * occasion interpretation extracted explicit product phrases (Task 8.5).
 *
 * <p>Deliberately never sets {@link RetailProductSearchRequest#category()}
 * (always {@code null}) - the whole point of the explicit-item pipeline is
 * to search using the user's own words and search terms, never a broad
 * catalog/generic category name, so the prompt built downstream never
 * narrows the search to a generic label that could lose meaning (e.g.
 * searching "TOP" instead of "USA soccer jersey").
 */
@Component
class RequestedItemSearchRequestFactory {

    /** Small, bounded candidate pool per item - enough to assemble up to three distinct outfits. */
    static final int CANDIDATES_PER_ITEM = 3;

    record RequestedItemSearchRequest(RequestedItem item, RetailProductSearchRequest request) {
    }

    List<RequestedItemSearchRequest> buildRequests(RecommendationContext context, List<RequestedItem> items) {
        BigDecimal perItemBudget = allocateBudgetPerItem(context.maxBudget(), items.size());
        TargetAudience targetAudience = toTargetAudience(context.shoppingDepartment());

        List<RequestedItemSearchRequest> requests = new ArrayList<>();
        for (RequestedItem item : items) {
            RetailProductSearchRequest request = new RetailProductSearchRequest(
                    Retailer.NORDSTROM,
                    null,
                    buildKeywords(context, targetAudience, item),
                    perItemBudget,
                    sizeFor(item, context),
                    targetAudience,
                    CANDIDATES_PER_ITEM);
            requests.add(new RequestedItemSearchRequest(item, request));
        }
        return requests;
    }

    private TargetAudience toTargetAudience(ShoppingDepartment shoppingDepartment) {
        return switch (shoppingDepartment) {
            case MEN -> TargetAudience.MEN;
            case WOMEN -> TargetAudience.WOMEN;
            case UNISEX -> TargetAudience.UNISEX;
            case NO_PREFERENCE -> TargetAudience.NO_PREFERENCE;
        };
    }

    /**
     * Splits the event's total budget evenly across every explicit item
     * (same soft-hint approach as {@link LiveCategorySearchRequestFactory}).
     */
    private BigDecimal allocateBudgetPerItem(BigDecimal totalBudget, int itemCount) {
        if (itemCount <= 0) {
            return totalBudget;
        }
        return totalBudget.divide(BigDecimal.valueOf(itemCount), 2, RoundingMode.DOWN);
    }

    /**
     * Keywords are built from the item itself (its exact phrase and its
     * normalized search-term variants) first - never only a generic
     * category name - plus event/activity context (preferred colors,
     * style, occasion, dress code, activity) and the department constraint.
     * Deduplicated and left unbounded in count (the underlying provider
     * request already bounds candidate results/limit; this only controls
     * prompt keyword recall for a single search call - it does not spawn
     * additional API calls per keyword).
     */
    private List<String> buildKeywords(RecommendationContext context, TargetAudience targetAudience, RequestedItem item) {
        Set<String> keywords = new LinkedHashSet<>();
        keywords.add(item.originalPhrase());
        keywords.addAll(item.searchTerms());
        if (item.activityContext() != null && !item.activityContext().isBlank()) {
            keywords.add(item.activityContext());
        }
        context.preferredColors().stream().limit(2).forEach(keywords::add);
        keywords.add(context.preferredStyle().name().toLowerCase(Locale.ROOT));
        keywords.add(context.occasion().name().toLowerCase(Locale.ROOT).replace('_', ' '));
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
        return List.copyOf(keywords);
    }

    /** No size hint for accessories/equipment (one-size-fits-all, same rule as the category pipeline). */
    private String sizeFor(RequestedItem item, RecommendationContext context) {
        if (item.genericCategory() == GenericItemCategory.ACCESSORY || item.genericCategory() == GenericItemCategory.EQUIPMENT) {
            return null;
        }
        return item.genericCategory() == GenericItemCategory.FOOTWEAR ? context.shoeSize() : context.clothingSize();
    }
}
