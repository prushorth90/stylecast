package com.stylecast.weather;

import java.time.OffsetDateTime;

/**
 * Retrieves forecast weather conditions for a location and time window.
 * Controllers and services must depend on this interface, never on a
 * concrete provider, per the "every external integration behind an
 * interface" architecture rule.
 */
public interface WeatherProvider {

    /**
     * A short, stable identifier for this provider (e.g. {@code "OPEN_METEO"}),
     * persisted alongside a snapshot for transparency.
     */
    String name();

    /**
     * How many days ahead of "now" this provider can reliably forecast.
     * Callers use this to decide whether an event is within the supported
     * forecast window before ever calling {@link #fetchForecast}.
     */
    int forecastHorizonDays();

    /**
     * @throws WeatherProviderException on a transient/provider failure (timeout, non-2xx, malformed response)
     */
    WeatherForecast fetchForecast(GeoCoordinates coordinates, OffsetDateTime startTime, OffsetDateTime endTime);
}
