package com.stylecast.retail;

import com.stylecast.catalog.ProductCategory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * A single normalized product candidate returned by a
 * {@link RetailProductSearchProvider}.
 *
 * <p>Every field the provider could not independently confirm from the
 * search result is left {@code null} (or empty for collections) rather than
 * guessed - see the "must not invent" rule in the class-level docs of
 * {@link RetailProductSearchProvider}.
 *
 * @param source               how this candidate was discovered
 * @param retailer             the retailer the product page belongs to
 * @param title                the product page title, as reported by the search result
 * @param category             optional catalog category, {@code null} if not independently known
 * @param price                optional price, {@code null} if not independently known
 * @param currency             optional ISO currency code, {@code null} if not independently known
 * @param productUrl           the validated {@code https://nordstrom.com/...} product page URL
 * @param imageUrl             optional product image URL, {@code null} if not independently known
 * @param description          optional short description, {@code null} if not independently known
 * @param availableSizes       sizes independently confirmed as available; never {@code null}, often empty
 * @param availabilityVerified always {@code false} unless a future provider independently verifies availability
 * @param retrievedAt          when this candidate was normalized by StyleCast
 * @param sourceCitation       optional short description of how the provider found this result
 */
public record RetailProductCandidate(
        RetailProductSource source,
        Retailer retailer,
        String title,
        ProductCategory category,
        BigDecimal price,
        String currency,
        String productUrl,
        String imageUrl,
        String description,
        List<String> availableSizes,
        boolean availabilityVerified,
        Instant retrievedAt,
        String sourceCitation
) {
    public RetailProductCandidate {
        availableSizes = availableSizes == null ? List.of() : List.copyOf(availableSizes);
    }
}
