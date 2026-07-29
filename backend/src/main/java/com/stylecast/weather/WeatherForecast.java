package com.stylecast.weather;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Forecast conditions for an event window, as returned by a
 * {@link WeatherProvider}. {@code forecastStart}/{@code forecastEnd} are the
 * actual provider timestamps used (nearest available hourly reading to the
 * event's start/end instants), which may differ slightly from the event's
 * own start/end times.
 */
public record WeatherForecast(
        OffsetDateTime forecastStart,
        OffsetDateTime forecastEnd,
        BigDecimal temperatureAtStart,
        BigDecimal temperatureAtEnd,
        Integer precipitationProbability,
        BigDecimal windSpeed,
        String condition
) {
}
