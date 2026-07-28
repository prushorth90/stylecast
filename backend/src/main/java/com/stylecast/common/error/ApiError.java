package com.stylecast.common.error;

import java.time.Instant;
import java.util.List;

/**
 * Consistent error response body used across the REST API.
 *
 * @param fieldErrors per-field validation failures, or {@code null} when the
 *                     error is not a field-validation error
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldError> fieldErrors
) {
    public record FieldError(String field, String message) {
    }
}
