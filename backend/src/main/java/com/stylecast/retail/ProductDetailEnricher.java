package com.stylecast.retail;

import java.util.Optional;

/**
 * Behind-the-interface boundary for attempting to enrich a single already-
 * validated Nordstrom product URL with additional, independently confirmed
 * detail (brand, price, sizes, stock text, etc.) beyond what a search
 * citation alone provides.
 *
 * <p>Implementations must:
 * <ul>
 *   <li>Never invent a field - only report what was genuinely confirmed for
 *       that exact URL.</li>
 *   <li>Never throw - any failure (timeout, error response, malformed or
 *       ungrounded output) must be caught internally and reported as {@link
 *       Optional#empty()}, so a failed enrichment attempt never discards an
 *       otherwise-valid candidate (the caller simply keeps the unenriched
 *       candidate).</li>
 *   <li>Apply a bounded timeout to whatever call(s) it makes.</li>
 * </ul>
 */
public interface ProductDetailEnricher {

    Optional<ProductPageDetails> enrich(String productUrl);
}
