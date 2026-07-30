package com.stylecast.recommendation;

/**
 * Where a recommendation's candidate products came from.
 *
 * <p>{@link #LOCAL_CATALOG} (Task 7A): the deterministic engine reads
 * exclusively from the locally seeded catalog ({@code com.stylecast.catalog})
 * and never calls a live retail provider or an LLM.
 *
 * <p>{@link #LIVE_NORDSTROM} (Task 8): candidates come from
 * {@code com.stylecast.retail}'s live Nordstrom product-search provider.
 * Every field on a {@code LIVE_NORDSTROM} item that was not independently
 * confirmed by that provider (price, size, availability) is left
 * unverified rather than invented - see {@code LiveOutfitItem}.
 */
public enum RecommendationSource {
    LOCAL_CATALOG,
    LIVE_NORDSTROM
}
