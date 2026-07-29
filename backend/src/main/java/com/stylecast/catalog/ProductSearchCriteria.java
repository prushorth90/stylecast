package com.stylecast.catalog;

import java.math.BigDecimal;

/**
 * Optional filters for {@code GET /api/products}, assembled by {@link
 * ProductController} from query parameters and validated/applied by {@link
 * CatalogService}.
 *
 * Enum-typed fields (category/preferredStyle/occasion/weather) are bound
 * directly from the query string by Spring's conversion service; an
 * unrecognized value throws {@code MethodArgumentTypeMismatchException},
 * already translated to HTTP 400 by the existing {@code
 * GlobalExceptionHandler}. Cross-field/semantic checks (negative price,
 * out-of-range or inverted formality bounds) are performed in {@link
 * CatalogService}.
 */
public record ProductSearchCriteria(
        ProductCategory category,
        String clothingSize,
        String color,
        BigDecimal maxPrice,
        StyleTag preferredStyle,
        OccasionTag occasion,
        WeatherTag weather,
        Integer minimumFormality,
        Integer maximumFormality,
        Boolean inStock
) {
}
