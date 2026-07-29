package com.stylecast.retail.dto;

import java.util.List;

/**
 * Response body for {@code POST /api/dev/retail-products/search}. An empty
 * {@code candidates} list with HTTP 200 means the search found zero valid
 * matches - it is not an error.
 */
public record RetailProductSearchApiResponse(List<RetailProductCandidateResponse> candidates) {
}
