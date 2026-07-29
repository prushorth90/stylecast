package com.stylecast.retail;

/**
 * Thrown by a {@link RetailProductSearchProvider} when it cannot complete a
 * search: missing/invalid provider configuration (e.g. no API key), a
 * network or provider-side timeout, a non-success response from the
 * provider, or a provider response so malformed it cannot be safely
 * interpreted at all. Mapped to HTTP 503 by {@code GlobalExceptionHandler} -
 * per Task 4B, a live-search failure must never silently fall back to
 * fictional local products.
 */
public class ProductSearchProviderException extends RuntimeException {

    public ProductSearchProviderException(String message) {
        super(message);
    }

    public ProductSearchProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
