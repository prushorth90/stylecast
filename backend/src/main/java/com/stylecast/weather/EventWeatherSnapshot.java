package com.stylecast.weather;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The latest weather lookup result for an event: either a real forecast
 * ({@link WeatherAvailabilityStatus#AVAILABLE}) or a record that the event is
 * outside the provider's supported forecast horizon
 * ({@link WeatherAvailabilityStatus#FORECAST_UNAVAILABLE}). Exactly one
 * snapshot exists per event; refreshing overwrites it rather than inserting
 * a new row.
 *
 * <p>This entity is intentionally kept out of the public REST contract; the
 * controller and service layers always translate to/from {@link
 * com.stylecast.weather.dto.EventWeatherResponse}.
 */
@Entity
@Table(name = "event_weather_snapshots")
public class EventWeatherSnapshot {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WeatherAvailabilityStatus status;

    @Column(name = "resolved_location", length = 300)
    private String resolvedLocation;

    @Column
    private BigDecimal latitude;

    @Column
    private BigDecimal longitude;

    @Column(name = "temperature_at_start")
    private BigDecimal temperatureAtStart;

    @Column(name = "temperature_at_end")
    private BigDecimal temperatureAtEnd;

    @Column(name = "precipitation_probability")
    private Integer precipitationProbability;

    @Column(name = "wind_speed")
    private BigDecimal windSpeed;

    @Column(length = 50)
    private String condition;

    @Column(name = "forecast_start")
    private OffsetDateTime forecastStart;

    @Column(name = "forecast_end")
    private OffsetDateTime forecastEnd;

    @Column(name = "retrieved_at", nullable = false)
    private Instant retrievedAt;

    @Column(name = "provider_name", length = 50)
    private String providerName;

    @Column(length = 500)
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EventWeatherSnapshot() {
        // JPA
    }

    public EventWeatherSnapshot(UUID id, UUID eventId, Instant now) {
        this.id = id;
        this.eventId = eventId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Records a successful forecast lookup, clearing any previous
     * unavailability message.
     */
    public void markAvailable(GeocodedLocation location, WeatherForecast forecast, String providerName, Instant now) {
        this.status = WeatherAvailabilityStatus.AVAILABLE;
        this.resolvedLocation = location.resolvedName();
        this.latitude = BigDecimal.valueOf(location.coordinates().latitude());
        this.longitude = BigDecimal.valueOf(location.coordinates().longitude());
        this.temperatureAtStart = forecast.temperatureAtStart();
        this.temperatureAtEnd = forecast.temperatureAtEnd();
        this.precipitationProbability = forecast.precipitationProbability();
        this.windSpeed = forecast.windSpeed();
        this.condition = forecast.condition();
        this.forecastStart = forecast.forecastStart();
        this.forecastEnd = forecast.forecastEnd();
        this.providerName = providerName;
        this.message = null;
        this.retrievedAt = now;
        this.updatedAt = now;
    }

    /**
     * Records that the event is outside the provider's supported forecast
     * horizon. Does not store any fabricated weather values - every
     * measurement field stays {@code null}.
     */
    public void markUnavailable(String message, Instant now) {
        this.status = WeatherAvailabilityStatus.FORECAST_UNAVAILABLE;
        this.resolvedLocation = null;
        this.latitude = null;
        this.longitude = null;
        this.temperatureAtStart = null;
        this.temperatureAtEnd = null;
        this.precipitationProbability = null;
        this.windSpeed = null;
        this.condition = null;
        this.forecastStart = null;
        this.forecastEnd = null;
        this.providerName = null;
        this.message = message;
        this.retrievedAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public WeatherAvailabilityStatus getStatus() {
        return status;
    }

    public String getResolvedLocation() {
        return resolvedLocation;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public BigDecimal getTemperatureAtStart() {
        return temperatureAtStart;
    }

    public BigDecimal getTemperatureAtEnd() {
        return temperatureAtEnd;
    }

    public Integer getPrecipitationProbability() {
        return precipitationProbability;
    }

    public BigDecimal getWindSpeed() {
        return windSpeed;
    }

    public String getCondition() {
        return condition;
    }

    public OffsetDateTime getForecastStart() {
        return forecastStart;
    }

    public OffsetDateTime getForecastEnd() {
        return forecastEnd;
    }

    public Instant getRetrievedAt() {
        return retrievedAt;
    }

    public String getProviderName() {
        return providerName;
    }

    public String getMessage() {
        return message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
