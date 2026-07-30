package com.stylecast.recommendation;

import com.stylecast.catalog.ProductCategory;
import com.stylecast.retail.RetailProductCandidate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Assembles up to three outfits from live Nordstrom search candidates
 * already grouped by required category.
 *
 * <p>Unlike the local-catalog engine, no optional categories or category
 * alternatives (e.g. SUIT vs BLAZER) are considered here (only required
 * categories are ever searched). An assembled outfit only ever includes
 * categories that actually found at least one candidate ({@link
 * #foundCategories}) - a category with zero candidates ({@link
 * #categoriesWithNoCandidates}) is simply omitted from every outfit rather
 * than blocking assembly entirely, so a search that found some but not all
 * required categories still returns valid, usable candidates for the ones
 * it did find (a "partial" result - see {@link LiveRecommendationCompleteness}).
 * {@link #assemble} only returns an empty list when literally no required
 * category found anything.
 *
 * <p>When a category has fewer candidates than {@link #MAX_OUTFITS}, its
 * best candidate is reused across multiple assembled outfits rather than
 * failing - e.g. if only one blazer was found but three pairs of shoes
 * were, outfits 1-3 all use that same blazer with a different pair of
 * shoes each.
 */
@Component
class LiveOutfitAssembler {

    static final int MAX_OUTFITS = 3;

    /**
     * Assembles up to {@link #MAX_OUTFITS} outfits using only the
     * categories that found at least one candidate ({@link
     * #foundCategories}); each outfit simply omits any required category
     * with zero candidates. Returns an empty list only when {@link
     * #foundCategories} itself is empty (nothing at all was found).
     */
    List<LiveAssembledOutfit> assemble(Map<ProductCategory, List<RetailProductCandidate>> candidatesByCategory, List<ProductCategory> requiredCategories) {
        List<ProductCategory> found = foundCategories(candidatesByCategory, requiredCategories);
        if (found.isEmpty()) {
            return List.of();
        }

        List<LiveAssembledOutfit> outfits = new ArrayList<>();
        Set<String> seenProductUrlSets = new LinkedHashSet<>();

        for (int index = 0; index < MAX_OUTFITS; index++) {
            List<LiveSelectedItem> items = new ArrayList<>();
            for (ProductCategory category : found) {
                List<RetailProductCandidate> candidates = candidatesByCategory.get(category);
                RetailProductCandidate candidate = candidates.get(Math.min(index, candidates.size() - 1));
                items.add(new LiveSelectedItem(category, candidate));
            }

            String key = productUrlKey(items);
            if (seenProductUrlSets.add(key)) {
                outfits.add(new LiveAssembledOutfit(items));
            }
        }

        return outfits;
    }

    /** Which required categories found at least one candidate. */
    List<ProductCategory> foundCategories(Map<ProductCategory, List<RetailProductCandidate>> candidatesByCategory, List<ProductCategory> requiredCategories) {
        return requiredCategories.stream()
                .filter(category -> !candidatesByCategory.getOrDefault(category, List.of()).isEmpty())
                .toList();
    }

    /** Which required categories (if any) had zero candidates. */
    List<ProductCategory> categoriesWithNoCandidates(Map<ProductCategory, List<RetailProductCandidate>> candidatesByCategory, List<ProductCategory> requiredCategories) {
        return requiredCategories.stream()
                .filter(category -> candidatesByCategory.getOrDefault(category, List.of()).isEmpty())
                .toList();
    }

    private String productUrlKey(List<LiveSelectedItem> items) {
        return items.stream()
                .map(item -> item.candidate().productUrl())
                .sorted()
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }

    record LiveSelectedItem(ProductCategory category, RetailProductCandidate candidate) {
    }

    record LiveAssembledOutfit(List<LiveSelectedItem> items) {
    }
}
