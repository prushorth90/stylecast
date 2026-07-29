package com.stylecast.weather;

/**
 * Thrown by a {@link WeatherProvider} when it cannot complete a forecast
 * request due to a transient failure: network/timeout error, a non-success
 * response, or a response so malformed it cannot be safely interpreted.
 * Mapped to HTTP 503 by {@code GlobalExceptionHandler} - never silently
 * falls back to fabricated weather data.
 */
public class WeatherProviderException extends RuntimeException {

    public WeatherProviderException(String message) {
        super(message);
    }

    public WeatherProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
