package com.stylecast.weather.dto;

import com.stylecast.weather.EventWeatherSnapshot;
import com.stylecast.weather.WeatherAvailabilityStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Public API representation of {@link EventWeatherSnapshot}.
 */
public record EventWeatherResponse(
        UUID id,
        UUID eventId,
        WeatherAvailabilityStatus status,
        String resolvedLocation,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal temperatureAtStart,
        BigDecimal temperatureAtEnd,
        Integer precipitationProbability,
        BigDecimal windSpeed,
        String condition,
        OffsetDateTime forecastStart,
        OffsetDateTime forecastEnd,
        Instant retrievedAt,
        String providerName,
        String message,
        boolean stale,
        String staleWarning
) {
    public static EventWeatherResponse fromEntity(EventWeatherSnapshot snapshot) {
        return build(snapshot, false, null);
    }

    /**
     * An automatic background refresh failed (transient provider/geocoding
     * failure) but a previous snapshot exists - returns that previous
     * snapshot unchanged, flagged as stale, with {@code staleWarning}
     * explaining why it couldn't be refreshed.
     */
    public static EventWeatherResponse stale(EventWeatherSnapshot snapshot, String staleWarning) {
        return build(snapshot, true, staleWarning);
    }

    private static EventWeatherResponse build(EventWeatherSnapshot snapshot, boolean stale, String staleWarning) {
        return new EventWeatherResponse(
                snapshot.getId(),
                snapshot.getEventId(),
                snapshot.getStatus(),
                snapshot.getResolvedLocation(),
                snapshot.getLatitude(),
                snapshot.getLongitude(),
                snapshot.getTemperatureAtStart(),
                snapshot.getTemperatureAtEnd(),
                snapshot.getPrecipitationProbability(),
                snapshot.getWindSpeed(),
                snapshot.getCondition(),
                snapshot.getForecastStart(),
                snapshot.getForecastEnd(),
                snapshot.getRetrievedAt(),
                snapshot.getProviderName(),
                snapshot.getMessage(),
                stale,
                staleWarning);
    }
}
