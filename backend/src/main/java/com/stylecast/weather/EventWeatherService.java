package com.stylecast.weather;

import com.stylecast.event.Event;
import com.stylecast.event.EventNotFoundException;
import com.stylecast.event.EventRepository;
import com.stylecast.weather.dto.EventWeatherResponse;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service for event-time weather. Enforces the
 * one-snapshot-per-event rule: refreshing either creates the first snapshot
 * for an event or overwrites the existing one, never inserting a duplicate.
 *
 * <p>{@link #getWeather(UUID)} is the primary entry point used when the
 * event styling page opens: it never requires a prior manual refresh -
 * it fetches automatically when no snapshot exists yet, reuses a saved
 * snapshot while it is still fresh (see {@link WeatherProperties#isFresh}),
 * and transparently refreshes a stale one. If that automatic refresh fails
 * but a previous snapshot exists, the previous snapshot is returned marked
 * stale with a warning rather than surfacing an error - only a failure with
 * no previous snapshot to fall back to propagates as an error.
 *
 * <p>A geocoding or weather provider failure ({@link GeocodingProviderException},
 * {@link UnresolvableLocationException}, {@link WeatherProviderException})
 * is never persisted - only a real forecast or a confirmed "outside the
 * forecast horizon" result is saved, so a transient failure never
 * overwrites a previously good snapshot.
 */
@Service
public class EventWeatherService {

    private final EventRepository eventRepository;
    private final EventWeatherSnapshotRepository snapshotRepository;
    private final GeocodingProvider geocodingProvider;
    private final WeatherProvider weatherProvider;
    private final WeatherProperties properties;

    public EventWeatherService(
            EventRepository eventRepository,
            EventWeatherSnapshotRepository snapshotRepository,
            GeocodingProvider geocodingProvider,
            WeatherProvider weatherProvider,
            WeatherProperties properties) {
        this.eventRepository = eventRepository;
        this.snapshotRepository = snapshotRepository;
        this.geocodingProvider = geocodingProvider;
        this.weatherProvider = weatherProvider;
        this.properties = properties;
    }

    /**
     * Returns the event's weather, fetching or refreshing it automatically
     * as needed - never requires a prior call to {@link #refreshWeather}.
     */
    public EventWeatherResponse getWeather(UUID eventId) {
        Event event = requireEvent(eventId);
        Instant now = Instant.now();
        Optional<EventWeatherSnapshot> existing = snapshotRepository.findByEventId(eventId);

        if (existing.isEmpty()) {
            EventWeatherSnapshot created = new EventWeatherSnapshot(UUID.randomUUID(), eventId, now);
            EventWeatherSnapshot saved = refreshSnapshot(event, created, now);
            return EventWeatherResponse.fromEntity(saved);
        }

        EventWeatherSnapshot snapshot = existing.get();
        if (properties.isFresh(snapshot.getRetrievedAt(), now)) {
            return EventWeatherResponse.fromEntity(snapshot);
        }

        try {
            EventWeatherSnapshot refreshed = refreshSnapshot(event, snapshot, now);
            return EventWeatherResponse.fromEntity(refreshed);
        } catch (UnresolvableLocationException | GeocodingProviderException | WeatherProviderException ex) {
            return EventWeatherResponse.stale(snapshot,
                    "Unable to refresh weather right now; showing the last known forecast. " + ex.getMessage());
        }
    }

    /**
     * Forces a fresh weather lookup regardless of the saved snapshot's age,
     * for the explicit "Refresh Weather" action. Unlike {@link #getWeather},
     * a provider failure here always propagates as an error rather than
     * falling back to stale data - the user asked for a refresh right now.
     */
    public EventWeatherResponse refreshWeather(UUID eventId) {
        Event event = requireEvent(eventId);
        Instant now = Instant.now();

        EventWeatherSnapshot snapshot = snapshotRepository.findByEventId(eventId)
                .orElseGet(() -> new EventWeatherSnapshot(UUID.randomUUID(), eventId, now));

        EventWeatherSnapshot saved = refreshSnapshot(event, snapshot, now);
        return EventWeatherResponse.fromEntity(saved);
    }

    private EventWeatherSnapshot refreshSnapshot(Event event, EventWeatherSnapshot snapshot, Instant now) {
        if (!isWithinForecastHorizon(event.getStartTime(), now)) {
            snapshot.markUnavailable(
                    "Event start time is beyond the %d-day forecast horizon"
                            .formatted(weatherProvider.forecastHorizonDays()),
                    now);
            return snapshotRepository.save(snapshot);
        }

        GeocodedLocation location = geocodingProvider.geocode(event.getLocation());
        WeatherForecast forecast = weatherProvider.fetchForecast(
                location.coordinates(), event.getStartTime(), event.getEndTime());

        snapshot.markAvailable(location, forecast, weatherProvider.name(), now);
        return snapshotRepository.save(snapshot);
    }

    private boolean isWithinForecastHorizon(OffsetDateTime eventStart, Instant now) {
        Instant horizonEnd = now.plus(Duration.ofDays(weatherProvider.forecastHorizonDays()));
        return !eventStart.toInstant().isAfter(horizonEnd);
    }

    private Event requireEvent(UUID eventId) {
        return eventRepository.findById(eventId).orElseThrow(() -> new EventNotFoundException(eventId));
    }
}
