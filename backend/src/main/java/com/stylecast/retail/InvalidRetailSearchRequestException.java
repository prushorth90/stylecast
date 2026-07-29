package com.stylecast.retail;

/**
 * A structurally invalid {@link RetailProductSearchRequest} (or its raw API
 * request form): missing both category and keywords, a non-positive
 * {@code maxPrice}, or a {@code limit} outside the supported range. Mapped to
 * HTTP 400 by {@code GlobalExceptionHandler}.
 */
public class InvalidRetailSearchRequestException extends RuntimeException {

    public InvalidRetailSearchRequestException(String message) {
        super(message);
    }
}
