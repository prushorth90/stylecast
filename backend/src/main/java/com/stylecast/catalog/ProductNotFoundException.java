package com.stylecast.catalog;

import java.util.UUID;

/**
 * Thrown when a requested product does not exist, or exists but is not
 * active. Translated to HTTP 404 by {@link com.stylecast.common.error.GlobalExceptionHandler}.
 */
public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(UUID productId) {
        super("Product not found: " + productId);
    }
}
