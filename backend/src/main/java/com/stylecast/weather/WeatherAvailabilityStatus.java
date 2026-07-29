package com.stylecast.weather;

/**
 * Whether an {@link EventWeatherSnapshot} holds a real forecast or reflects
 * an event outside the provider's supported forecast horizon.
 */
public enum WeatherAvailabilityStatus {
    AVAILABLE,
    FORECAST_UNAVAILABLE
}
