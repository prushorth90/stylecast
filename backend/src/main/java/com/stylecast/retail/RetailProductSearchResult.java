package com.stylecast.retail;

import java.util.List;

/**
 * The result of a {@link RetailProductSearchProvider} search: zero or more
 * normalized, deduplicated {@link RetailProductCandidate}s. Zero candidates
 * (an empty list) is a valid, non-error outcome.
 *
 * @param candidates normalized candidates, deduplicated by canonical product URL
 */
public record RetailProductSearchResult(List<RetailProductCandidate> candidates) {
    public RetailProductSearchResult {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }
}
