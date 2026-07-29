package com.stylecast.weather;

import com.stylecast.event.Event;
import com.stylecast.event.EventNotFoundException;
import com.stylecast.event.EventRepository;
import com.stylecast.event.EventSetting;
import com.stylecast.weather.dto.EventWeatherResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventWeatherServiceTest {

    private static final long FRESHNESS_MINUTES = 180;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventWeatherSnapshotRepository snapshotRepository;

    @Mock
    private GeocodingProvider geocodingProvider;

    @Mock
    private WeatherProvider weatherProvider;

    private WeatherProperties properties;
    private EventWeatherService service;

    @BeforeEach
    void setUp() {
        properties = new WeatherProperties(
                "http://localhost", "http://localhost", 1000, 1000, 16, FRESHNESS_MINUTES, "OPEN_METEO");
        service = new EventWeatherService(
                eventRepository, snapshotRepository, geocodingProvider, weatherProvider, properties);
        lenient().when(weatherProvider.forecastHorizonDays()).thenReturn(16);
        lenient().when(weatherProvider.name()).thenReturn("OPEN_METEO");
    }

    private Event eventStartingIn(UUID eventId, long daysFromNow) {
        OffsetDateTime start = OffsetDateTime.now().plusDays(daysFromNow);
        return new Event(
                eventId, "Some event", "Description", "123 Main St, Springfield",
                start, start.plusHours(3), EventSetting.OUTDOOR, "Casual", Instant.now());
    }

    private EventWeatherSnapshot availableSnapshot(UUID eventId, Instant retrievedAt) {
        EventWeatherSnapshot snapshot = new EventWeatherSnapshot(UUID.randomUUID(), eventId, retrievedAt);
        snapshot.markAvailable(
                new GeocodedLocation("Springfield, USA", new GeoCoordinates(39.0, -89.0)),
                new WeatherForecast(
                        OffsetDateTime.now(), OffsetDateTime.now().plusHours(3),
                        new BigDecimal("20.0"), new BigDecimal("18.0"), 10, new BigDecimal("5.0"), "Clear sky"),
                "OPEN_METEO", retrievedAt);
        return snapshot;
    }

    // --- getWeather -----------------------------------------------------

    @Test
    void getWeather_withUnknownEvent_throws404() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getWeather(eventId))
                .isInstanceOf(EventNotFoundException.class);
    }

    @Test
    void getWeather_withNoSnapshot_automaticallyFetchesFromProviderAndPersists() {
        UUID eventId = UUID.randomUUID();
        Event event = eventStartingIn(eventId, 2);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(snapshotRepository.findByEventId(eventId)).thenReturn(Optional.empty());

        GeocodedLocation location = new GeocodedLocation("Springfield, USA", new GeoCoordinates(39.0, -89.0));
        WeatherForecast forecast = new WeatherForecast(
                event.getStartTime(), event.getEndTime(),
                new BigDecimal("22.5"), new BigDecimal("19.0"), 15, new BigDecimal("8.0"), "Partly cloudy");
        when(geocodingProvider.geocode(event.getLocation())).thenReturn(location);
        when(weatherProvider.fetchForecast(location.coordinates(), event.getStartTime(), event.getEndTime()))
                .thenReturn(forecast);
        when(snapshotRepository.save(any(EventWeatherSnapshot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EventWeatherResponse response = service.getWeather(eventId);

        verify(geocodingProvider).geocode(event.getLocation());
        verify(snapshotRepository).save(any(EventWeatherSnapshot.class));
        assertThat(response.status()).isEqualTo(WeatherAvailabilityStatus.AVAILABLE);
        assertThat(response.condition()).isEqualTo("Partly cloudy");
        assertThat(response.stale()).isFalse();
    }

    @Test
    void getWeather_withNoSnapshotAndProviderFailure_propagatesWithNoDataToFallBackTo() {
        UUID eventId = UUID.randomUUID();
        Event event = eventStartingIn(eventId, 2);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(snapshotRepository.findByEventId(eventId)).thenReturn(Optional.empty());
        when(geocodingProvider.geocode(event.getLocation()))
                .thenThrow(new GeocodingProviderException("timeout"));

        assertThatThrownBy(() -> service.getWeather(eventId))
                .isInstanceOf(GeocodingProviderException.class);

        verify(snapshotRepository, never()).save(any());
    }

    @Test
    void getWeather_withFreshSnapshot_returnsSavedDataWithoutCallingAnyProvider() {
        UUID eventId = UUID.randomUUID();
        EventWeatherSnapshot snapshot = availableSnapshot(eventId, Instant.now());
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(eventStartingIn(eventId, 1)));
        when(snapshotRepository.findByEventId(eventId)).thenReturn(Optional.of(snapshot));

        EventWeatherResponse response = service.getWeather(eventId);

        assertThat(response.eventId()).isEqualTo(eventId);
        assertThat(response.status()).isEqualTo(WeatherAvailabilityStatus.AVAILABLE);
        assertThat(response.condition()).isEqualTo("Clear sky");
        assertThat(response.stale()).isFalse();
        verifyNoInteractions(geocodingProvider, weatherProvider);
        verify(snapshotRepository, never()).save(any());
    }

    @Test
    void getWeather_withStaleSnapshot_refreshesAndReturnsUpdatedData() {
        UUID eventId = UUID.randomUUID();
        Event event = eventStartingIn(eventId, 2);
        Instant staleRetrievedAt = Instant.now().minus(java.time.Duration.ofMinutes(FRESHNESS_MINUTES + 1));
        EventWeatherSnapshot snapshot = availableSnapshot(eventId, staleRetrievedAt);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(snapshotRepository.findByEventId(eventId)).thenReturn(Optional.of(snapshot));

        GeocodedLocation location = new GeocodedLocation("Somewhere", new GeoCoordinates(1.0, 2.0));
        WeatherForecast forecast = new WeatherForecast(
                event.getStartTime(), event.getEndTime(),
                new BigDecimal("30.0"), new BigDecimal("28.0"), 60, new BigDecimal("15.0"), "Thunderstorm");
        when(geocodingProvider.geocode(event.getLocation())).thenReturn(location);
        when(weatherProvider.fetchForecast(any(), any(), any())).thenReturn(forecast);
        when(snapshotRepository.save(any(EventWeatherSnapshot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EventWeatherResponse response = service.getWeather(eventId);

        verify(geocodingProvider).geocode(event.getLocation());
        verify(snapshotRepository).save(snapshot);
        assertThat(response.condition()).isEqualTo("Thunderstorm");
        assertThat(response.temperatureAtStart()).isEqualByComparingTo("30.0");
        assertThat(response.stale()).isFalse();
    }

    @Test
    void getWeather_withStaleSnapshotAndProviderFailure_returnsPreviousSnapshotMarkedStale() {
        UUID eventId = UUID.randomUUID();
        Event event = eventStartingIn(eventId, 2);
        Instant staleRetrievedAt = Instant.now().minus(java.time.Duration.ofMinutes(FRESHNESS_MINUTES + 1));
        EventWeatherSnapshot snapshot = availableSnapshot(eventId, staleRetrievedAt);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(snapshotRepository.findByEventId(eventId)).thenReturn(Optional.of(snapshot));
        when(geocodingProvider.geocode(event.getLocation()))
                .thenThrow(new WeatherProviderException("provider unavailable"));

        EventWeatherResponse response = service.getWeather(eventId);

        assertThat(response.stale()).isTrue();
        assertThat(response.staleWarning()).contains("provider unavailable");
        // Still reflects the OLD (previously saved) data, not fabricated/blank values.
        assertThat(response.condition()).isEqualTo("Clear sky");
        assertThat(response.temperatureAtStart()).isEqualByComparingTo("20.0");
        assertThat(response.retrievedAt()).isEqualTo(staleRetrievedAt);
        // The failed refresh attempt must not overwrite the previously saved snapshot.
        verify(snapshotRepository, never()).save(any());
    }

    @Test
    void getWeather_withStaleSnapshotAndUnresolvableLocation_returnsPreviousSnapshotMarkedStale() {
        UUID eventId = UUID.randomUUID();
        Event event = eventStartingIn(eventId, 2);
        Instant staleRetrievedAt = Instant.now().minus(java.time.Duration.ofMinutes(FRESHNESS_MINUTES + 1));
        EventWeatherSnapshot snapshot = availableSnapshot(eventId, staleRetrievedAt);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(snapshotRepository.findByEventId(eventId)).thenReturn(Optional.of(snapshot));
        when(geocodingProvider.geocode(event.getLocation()))
                .thenThrow(new UnresolvableLocationException(event.getLocation()));

        EventWeatherResponse response = service.getWeather(eventId);

        assertThat(response.stale()).isTrue();
        assertThat(response.condition()).isEqualTo("Clear sky");
        verify(snapshotRepository, never()).save(any());
    }

    // --- refreshWeather ---------------------------------------------------

    @Test
    void refreshWeather_withUnknownEvent_throwsAndNeverSaves() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refreshWeather(eventId))
                .isInstanceOf(EventNotFoundException.class);

        verify(snapshotRepository, never()).save(any());
    }

    @Test
    void refreshWeather_withinHorizon_geocodesFetchesAndSavesAvailableSnapshot() {
        UUID eventId = UUID.randomUUID();
        Event event = eventStartingIn(eventId, 2);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(snapshotRepository.findByEventId(eventId)).thenReturn(Optional.empty());

        GeocodedLocation location = new GeocodedLocation("Springfield, USA", new GeoCoordinates(39.0, -89.0));
        WeatherForecast forecast = new WeatherForecast(
                event.getStartTime(), event.getEndTime(),
                new BigDecimal("22.5"), new BigDecimal("19.0"), 15, new BigDecimal("8.0"), "Partly cloudy");
        when(geocodingProvider.geocode(event.getLocation())).thenReturn(location);
        when(weatherProvider.fetchForecast(location.coordinates(), event.getStartTime(), event.getEndTime()))
                .thenReturn(forecast);
        when(snapshotRepository.save(any(EventWeatherSnapshot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EventWeatherResponse response = service.refreshWeather(eventId);

        ArgumentCaptor<EventWeatherSnapshot> captor = ArgumentCaptor.forClass(EventWeatherSnapshot.class);
        verify(snapshotRepository).save(captor.capture());
        EventWeatherSnapshot saved = captor.getValue();

        assertThat(saved.getStatus()).isEqualTo(WeatherAvailabilityStatus.AVAILABLE);
        assertThat(saved.getResolvedLocation()).isEqualTo("Springfield, USA");
        assertThat(saved.getTemperatureAtStart()).isEqualByComparingTo("22.5");
        assertThat(saved.getTemperatureAtEnd()).isEqualByComparingTo("19.0");
        assertThat(saved.getPrecipitationProbability()).isEqualTo(15);
        assertThat(saved.getWindSpeed()).isEqualByComparingTo("8.0");
        assertThat(saved.getCondition()).isEqualTo("Partly cloudy");
        assertThat(saved.getProviderName()).isEqualTo("OPEN_METEO");
        assertThat(saved.getMessage()).isNull();
        assertThat(response.status()).isEqualTo(WeatherAvailabilityStatus.AVAILABLE);
    }

    @Test
    void refreshWeather_replacesExistingSnapshotInPlace() {
        UUID eventId = UUID.randomUUID();
        Event event = eventStartingIn(eventId, 2);
        EventWeatherSnapshot existing = new EventWeatherSnapshot(UUID.randomUUID(), eventId, Instant.now());
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(snapshotRepository.findByEventId(eventId)).thenReturn(Optional.of(existing));

        GeocodedLocation location = new GeocodedLocation("Somewhere", new GeoCoordinates(1.0, 2.0));
        WeatherForecast forecast = new WeatherForecast(
                event.getStartTime(), event.getEndTime(),
                new BigDecimal("10.0"), new BigDecimal("9.0"), 5, new BigDecimal("3.0"), "Clear sky");
        when(geocodingProvider.geocode(event.getLocation())).thenReturn(location);
        when(weatherProvider.fetchForecast(any(), any(), any())).thenReturn(forecast);
        when(snapshotRepository.save(any(EventWeatherSnapshot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.refreshWeather(eventId);

        ArgumentCaptor<EventWeatherSnapshot> captor = ArgumentCaptor.forClass(EventWeatherSnapshot.class);
        verify(snapshotRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(existing.getId());
    }

    @Test
    void refreshWeather_beyondForecastHorizon_savesUnavailableWithoutCallingProviders() {
        UUID eventId = UUID.randomUUID();
        Event event = eventStartingIn(eventId, 30);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(snapshotRepository.findByEventId(eventId)).thenReturn(Optional.empty());
        when(snapshotRepository.save(any(EventWeatherSnapshot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EventWeatherResponse response = service.refreshWeather(eventId);

        verifyNoInteractions(geocodingProvider);
        ArgumentCaptor<EventWeatherSnapshot> captor = ArgumentCaptor.forClass(EventWeatherSnapshot.class);
        verify(snapshotRepository).save(captor.capture());
        EventWeatherSnapshot saved = captor.getValue();

        assertThat(saved.getStatus()).isEqualTo(WeatherAvailabilityStatus.FORECAST_UNAVAILABLE);
        assertThat(saved.getTemperatureAtStart()).isNull();
        assertThat(saved.getTemperatureAtEnd()).isNull();
        assertThat(saved.getPrecipitationProbability()).isNull();
        assertThat(saved.getWindSpeed()).isNull();
        assertThat(saved.getCondition()).isNull();
        assertThat(saved.getResolvedLocation()).isNull();
        assertThat(saved.getMessage()).contains("16-day forecast horizon");
        assertThat(response.status()).isEqualTo(WeatherAvailabilityStatus.FORECAST_UNAVAILABLE);
        assertThat(response.temperatureAtStart()).isNull();
    }

    @Test
    void refreshWeather_withUnresolvableLocation_propagatesWithoutSaving() {
        UUID eventId = UUID.randomUUID();
        Event event = eventStartingIn(eventId, 2);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(snapshotRepository.findByEventId(eventId)).thenReturn(Optional.empty());
        when(geocodingProvider.geocode(event.getLocation()))
                .thenThrow(new UnresolvableLocationException(event.getLocation()));

        assertThatThrownBy(() -> service.refreshWeather(eventId))
                .isInstanceOf(UnresolvableLocationException.class);

        verify(snapshotRepository, never()).save(any());
    }

    @Test
    void refreshWeather_withGeocodingProviderFailure_propagatesWithoutSaving() {
        UUID eventId = UUID.randomUUID();
        Event event = eventStartingIn(eventId, 2);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(snapshotRepository.findByEventId(eventId)).thenReturn(Optional.empty());
        when(geocodingProvider.geocode(event.getLocation()))
                .thenThrow(new GeocodingProviderException("timeout"));

        assertThatThrownBy(() -> service.refreshWeather(eventId))
                .isInstanceOf(GeocodingProviderException.class);

        verify(snapshotRepository, never()).save(any());
    }

    @Test
    void refreshWeather_withWeatherProviderFailure_propagatesWithoutSaving() {
        UUID eventId = UUID.randomUUID();
        Event event = eventStartingIn(eventId, 2);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(snapshotRepository.findByEventId(eventId)).thenReturn(Optional.empty());
        GeocodedLocation location = new GeocodedLocation("Somewhere", new GeoCoordinates(1.0, 2.0));
        when(geocodingProvider.geocode(event.getLocation())).thenReturn(location);
        when(weatherProvider.fetchForecast(any(), any(), any()))
                .thenThrow(new WeatherProviderException("provider timeout"));

        assertThatThrownBy(() -> service.refreshWeather(eventId))
                .isInstanceOf(WeatherProviderException.class);

        verify(snapshotRepository, never()).save(any());
    }
}
