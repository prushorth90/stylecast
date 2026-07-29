package com.stylecast.weather;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.time.Instant;

/**
 * Configuration for weather geocoding + forecast retrieval, bound from
 * {@code stylecast.weather.*} (see application.yml). Every value has a
 * default: the default provider (Open-Meteo) requires no API key, so the
 * application starts and the weather feature works out of the box without
 * any extra secrets.
 *
 * @param geocodingBaseUrl   base URL of the geocoding API; overridable in tests to
 *                           point at a local fake HTTP server instead of the real API
 * @param forecastBaseUrl    base URL of the forecast API; overridable in tests to
 *                           point at a local fake HTTP server instead of the real API
 * @param connectTimeoutMs   HTTP connect timeout in milliseconds, for both APIs
 * @param readTimeoutMs      HTTP response timeout in milliseconds, for both APIs
 * @param forecastHorizonDays how many days ahead of "now" the forecast provider can
 *                            reliably forecast; events further out are reported as
 *                            {@link WeatherAvailabilityStatus#FORECAST_UNAVAILABLE}
 * @param freshnessMinutes   how long a saved snapshot is considered fresh; a
 *                           {@code GET} within this window returns the saved
 *                           snapshot without calling any provider, a {@code GET}
 *                           past it triggers an automatic refresh
 * @param providerName       short identifier persisted alongside a snapshot (e.g. {@code "OPEN_METEO"})
 */
@ConfigurationProperties(prefix = "stylecast.weather")
public record WeatherProperties(
        String geocodingBaseUrl,
        String forecastBaseUrl,
        long connectTimeoutMs,
        long readTimeoutMs,
        int forecastHorizonDays,
        long freshnessMinutes,
        String providerName
) {
    public boolean isFresh(Instant retrievedAt, Instant now) {
        return now.isBefore(retrievedAt.plus(Duration.ofMinutes(freshnessMinutes)));
    }
}
