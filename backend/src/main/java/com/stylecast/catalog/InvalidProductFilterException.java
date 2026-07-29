package com.stylecast.catalog;

/**
 * Thrown when catalog search/filter parameters fail cross-field or
 * semantic validation (e.g. a negative price, an out-of-range formality
 * level, or an inverted formality range). Translated to HTTP 400 by {@link
 * com.stylecast.common.error.GlobalExceptionHandler}.
 *
 * Per-field type validation (e.g. an unrecognized enum value) is instead
 * handled automatically by Spring's existing {@code
 * MethodArgumentTypeMismatchException} handling, since query parameters are
 * bound directly to enum types.
 */
public class InvalidProductFilterException extends RuntimeException {

    public InvalidProductFilterException(String message) {
        super(message);
    }
}
