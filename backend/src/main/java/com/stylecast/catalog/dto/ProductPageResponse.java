package com.stylecast.catalog.dto;

import java.util.List;

/**
 * Paginated response body for {@code GET /api/products}.
 *
 * Deliberately a concrete (non-generic) record rather than a generic {@code
 * PageResponse<T>}: a generic record combined with Jackson's
 * ParameterizedTypeReference-based deserialization (as used by tests via
 * TestRestTemplate) triggered spurious "Cannot map null into type int"
 * errors for the record's non-generic fields. A concrete type avoids that
 * entirely; if a second paginated endpoint is added later, revisit
 * extracting a shared shape then.
 */
public record ProductPageResponse(
        List<ProductSummaryResponse> content,
        int page,
        int pageSize,
        long totalElements,
        int totalPages
) {
}
