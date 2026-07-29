package com.stylecast.retail;

/**
 * Identifies how a {@link RetailProductCandidate} was discovered. Kept
 * separate from {@link Retailer} (which product site the candidate is on) so
 * a future provider (e.g. a different search API) can be distinguished from
 * this one without changing the retailer.
 */
public enum RetailProductSource {
    /** Discovered via an OpenAI Responses API {@code web_search} tool call. */
    AI_WEB_SEARCH
}
