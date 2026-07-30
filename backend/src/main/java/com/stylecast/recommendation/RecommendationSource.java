package com.stylecast.recommendation;

/**
 * Where a recommendation's candidate products came from.
 *
 * <p>Task 7A only ever produces {@link #LOCAL_CATALOG}: the deterministic
 * engine reads exclusively from the locally seeded catalog
 * ({@code com.stylecast.catalog}) and never calls a live retail provider or
 * an LLM. A future task may add a live-retail-backed value; nothing in this
 * module does today.
 */
public enum RecommendationSource {
    LOCAL_CATALOG
}
