package com.stylecast.weather;

/**
 * Thrown by a {@link GeocodingProvider} when it cannot complete a geocoding
 * request due to a transient failure: network/timeout error, a non-success
 * response, or a response so malformed it cannot be safely interpreted.
 * Mapped to HTTP 503 by {@code GlobalExceptionHandler} - never silently
 * falls back to fabricated coordinates.
 */
public class GeocodingProviderException extends RuntimeException {

    public GeocodingProviderException(String message) {
        super(message);
    }

    public GeocodingProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
