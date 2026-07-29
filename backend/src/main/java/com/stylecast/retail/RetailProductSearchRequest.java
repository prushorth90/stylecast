package com.stylecast.retail;

import com.stylecast.catalog.ProductCategory;

import java.math.BigDecimal;
import java.util.List;

/**
 * A validated, structured product-search request passed to a
 * {@link RetailProductSearchProvider}.
 *
 * <p>Instances are built (and validated) by {@link RetailProductSearchService}
 * - by the time a provider sees one, {@code retailer} is supported,
 * {@code limit} is resolved to a positive bounded value, at least one of
 * {@code category}/{@code keywords} is present, and {@code maxPrice} (if any)
 * is strictly positive.
 *
 * @param retailer     the retailer to restrict results to
 * @param category     optional catalog category hint for the search
 * @param keywords     free-text keywords describing the desired product(s);
 *                     never {@code null}, may be empty if {@code category} is present
 * @param maxPrice     optional maximum price in USD
 * @param clothingSize optional clothing size hint
 * @param limit        maximum number of candidates to return, already bounded
 */
public record RetailProductSearchRequest(
        Retailer retailer,
        ProductCategory category,
        List<String> keywords,
        BigDecimal maxPrice,
        String clothingSize,
        int limit
) {
    public RetailProductSearchRequest {
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
    }
}
