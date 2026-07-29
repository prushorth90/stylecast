package com.stylecast.retail.dto;

import com.stylecast.catalog.ProductCategory;
import com.stylecast.retail.Retailer;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * Request body for the temporary development endpoint
 * {@code POST /api/dev/retail-products/search}. Cross-field business rules
 * (at least one of {@code category}/{@code keywords}, positive
 * {@code maxPrice}, bounded {@code limit}) are enforced by
 * {@code RetailProductSearchService}, not by Bean Validation here, since
 * they depend on runtime configuration (e.g. the configured max result
 * limit).
 *
 * @param retailer     required; currently only {@link Retailer#NORDSTROM}
 * @param category     optional catalog category
 * @param keywords     optional free-text keywords; treated as empty if omitted
 * @param maxPrice     optional maximum price in USD
 * @param clothingSize optional clothing size hint
 * @param limit        optional maximum number of results; defaults to 10 if omitted
 */
public record RetailProductSearchApiRequest(
        @NotNull Retailer retailer,
        ProductCategory category,
        List<String> keywords,
        BigDecimal maxPrice,
        String clothingSize,
        Integer limit
) {
}
