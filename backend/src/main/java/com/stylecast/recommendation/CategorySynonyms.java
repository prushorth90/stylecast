package com.stylecast.recommendation;

import com.stylecast.catalog.ProductCategory;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic, hand-curated search-keyword synonyms per garment category -
 * improves live-search recall (e.g. "dress pants" is a common real-world
 * synonym for formal trousers) without adding extra search calls: all
 * synonyms for a category are folded into that category's single search
 * request's keywords (see {@link LiveCategorySearchRequestFactory}), never
 * used to spawn additional per-synonym API calls - this is what keeps the
 * added recall bounded/cheap rather than multiplying request volume.
 *
 * <p>Categories not listed here have no forced synonyms (empty list) -
 * their existing keyword derivation (colors, style, occasion, dress code,
 * department) still applies unchanged.
 */
final class CategorySynonyms {

    private static final Map<ProductCategory, List<String>> SYNONYMS = buildSynonyms();

    private CategorySynonyms() {
    }

    /** Never {@code null}; empty for a category with no defined synonyms. */
    static List<String> synonymsFor(ProductCategory category) {
        return SYNONYMS.getOrDefault(category, List.of());
    }

    private static Map<ProductCategory, List<String>> buildSynonyms() {
        Map<ProductCategory, List<String>> synonyms = new EnumMap<>(ProductCategory.class);
        synonyms.put(ProductCategory.TROUSERS, List.of("trousers", "dress pants", "pants", "chinos"));
        synonyms.put(ProductCategory.SHIRT, List.of("shirt", "dress shirt", "button-up shirt"));
        synonyms.put(ProductCategory.SHOES, List.of("dress shoes", "loafers", "oxfords"));
        synonyms.put(ProductCategory.ACCESSORY, List.of("tie", "belt", "pocket square"));
        synonyms.put(ProductCategory.SUIT, List.of("suit", "tuxedo", "dinner jacket"));
        return Map.copyOf(synonyms);
    }
}
