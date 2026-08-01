package com.stylecast.weather;

import com.stylecast.common.error.ApiError;
import com.stylecast.event.Event;
import com.stylecast.event.EventRepository;
import com.stylecast.event.EventSetting;
import com.stylecast.weather.dto.EventWeatherResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import com.stylecast.testsupport.NoExternalNetworkGuardConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-request tests for {@code GET /api/events/{eventId}/weather} and
 * {@code POST /api/events/{eventId}/weather/refresh}. The real
 * {@link GeocodingProvider}/{@link WeatherProvider} beans are replaced with
 * fakes ({@link FakeProvidersConfig}) so these tests never call the live
 * Open-Meteo API - provider-specific parsing/HTTP behavior is covered by
 * {@code OpenMeteoGeocodingProviderTest}/{@code OpenMeteoWeatherProviderTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
@ActiveProfiles("test")
@Import(NoExternalNetworkGuardConfig.class)
class EventWeatherControllerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventWeatherSnapshotRepository snapshotRepository;

    @Autowired
    private FakeGeocodingProvider fakeGeocodingProvider;

    @Autowired
    private FakeWeatherProvider fakeWeatherProvider;

    private UUID eventId;

    @BeforeEach
    void setUp() {
        snapshotRepository.deleteAll();
        eventRepository.deleteAll();
        fakeGeocodingProvider.reset();
        fakeWeatherProvider.reset();

        Event event = eventRepository.save(new Event(
                UUID.randomUUID(),
                "Rooftop birthday party",
                "Casual outdoor birthday celebration",
                "123 Main St, Springfield",
                OffsetDateTime.now().plusDays(1),
                OffsetDateTime.now().plusDays(1).plusHours(3),
                EventSetting.OUTDOOR,
                "Smart casual",
                Instant.now()));
        eventId = event.getId();
    }

    @AfterEach
    void resetFakes() {
        fakeGeocodingProvider.reset();
        fakeWeatherProvider.reset();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void getWeather_forUnknownEvent_returns404() {
        ResponseEntity<ApiError> response =
                restTemplate.getForEntity(url("/api/events/" + UUID.randomUUID() + "/weather"), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getWeather_withMalformedEventId_returns400() {
        ResponseEntity<ApiError> response =
                restTemplate.getForEntity(url("/api/events/not-a-uuid/weather"), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getWeather_withNoSnapshot_automaticallyFetchesFromProviderAndPersists() {
        fakeGeocodingProvider.nextLocation.set(
                new GeocodedLocation("Springfield, Illinois, United States", new GeoCoordinates(39.78, -89.65)));
        fakeWeatherProvider.nextForecast.set(new WeatherForecast(
                OffsetDateTime.now().plusDays(1), OffsetDateTime.now().plusDays(1).plusHours(3),
                new BigDecimal("21.5"), new BigDecimal("18.0"), 20, new BigDecimal("9.5"), "Partly cloudy"));

        ResponseEntity<EventWeatherResponse> response =
                restTemplate.getForEntity(url("/api/events/" + eventId + "/weather"), EventWeatherResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(WeatherAvailabilityStatus.AVAILABLE);
        assertThat(response.getBody().temperatureAtStart()).isEqualByComparingTo("21.5");
        assertThat(response.getBody().stale()).isFalse();
        assertThat(snapshotRepository.findAll()).hasSize(1);
    }

    @Test
    void getWeather_withNoSnapshotAndProviderFailure_returnsTheProviderError() {
        fakeGeocodingProvider.nextFailure.set(new GeocodingProviderException("geocoding timed out"));

        ResponseEntity<ApiError> response =
                restTemplate.getForEntity(url("/api/events/" + eventId + "/weather"), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(snapshotRepository.findAll()).isEmpty();
    }

    @Test
    void getWeather_withFreshSnapshot_returnsSavedDataWithoutCallingProviderAgain() {
        EventWeatherSnapshot snapshot = new EventWeatherSnapshot(UUID.randomUUID(), eventId, Instant.now());
        snapshot.markAvailable(
                new GeocodedLocation("Original location", new GeoCoordinates(10.0, 20.0)),
                new WeatherForecast(
                        OffsetDateTime.now(), OffsetDateTime.now().plusHours(1),
                        new BigDecimal("15.0"), new BigDecimal("14.0"), 5, new BigDecimal("2.0"), "Clear sky"),
                "OPEN_METEO", Instant.now());
        snapshotRepository.save(snapshot);

        // If the (still-fresh) snapshot were refreshed, this different data would be returned instead.
        fakeGeocodingProvider.nextLocation.set(new GeocodedLocation("Different location", new GeoCoordinates(1.0, 1.0)));
        fakeWeatherProvider.nextForecast.set(new WeatherForecast(
                OffsetDateTime.now(), OffsetDateTime.now().plusHours(1),
                new BigDecimal("99.0"), new BigDecimal("99.0"), 99, new BigDecimal("99.0"), "Thunderstorm"));

        ResponseEntity<EventWeatherResponse> response =
                restTemplate.getForEntity(url("/api/events/" + eventId + "/weather"), EventWeatherResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().resolvedLocation()).isEqualTo("Original location");
        assertThat(response.getBody().temperatureAtStart()).isEqualByComparingTo("15.0");
        assertThat(response.getBody().stale()).isFalse();
        assertThat(snapshotRepository.findAll()).hasSize(1);
    }

    @Test
    void getWeather_withStaleSnapshot_automaticallyRefreshesAndReturnsNewData() {
        Instant staleRetrievedAt = Instant.now().minus(Duration.ofHours(4));
        EventWeatherSnapshot snapshot = new EventWeatherSnapshot(UUID.randomUUID(), eventId, staleRetrievedAt);
        snapshot.markAvailable(
                new GeocodedLocation("Old location", new GeoCoordinates(10.0, 20.0)),
                new WeatherForecast(
                        OffsetDateTime.now(), OffsetDateTime.now().plusHours(1),
                        new BigDecimal("15.0"), new BigDecimal("14.0"), 5, new BigDecimal("2.0"), "Clear sky"),
                "OPEN_METEO", staleRetrievedAt);
        snapshotRepository.save(snapshot);

        fakeGeocodingProvider.nextLocation.set(new GeocodedLocation("New location", new GeoCoordinates(1.0, 1.0)));
        fakeWeatherProvider.nextForecast.set(new WeatherForecast(
                OffsetDateTime.now(), OffsetDateTime.now().plusHours(1),
                new BigDecimal("30.0"), new BigDecimal("28.0"), 60, new BigDecimal("15.0"), "Thunderstorm"));

        ResponseEntity<EventWeatherResponse> response =
                restTemplate.getForEntity(url("/api/events/" + eventId + "/weather"), EventWeatherResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().resolvedLocation()).isEqualTo("New location");
        assertThat(response.getBody().temperatureAtStart()).isEqualByComparingTo("30.0");
        assertThat(response.getBody().stale()).isFalse();
        assertThat(snapshotRepository.findAll()).hasSize(1);
    }

    @Test
    void getWeather_withStaleSnapshotAndRefreshFailure_returnsPreviousSnapshotMarkedStale() {
        // Truncated to microseconds: PostgreSQL's TIMESTAMPTZ column only stores
        // microsecond precision, so a nanosecond-precision Instant.now() would
        // never compare equal to the value reloaded from the database below.
        Instant staleRetrievedAt = Instant.now().minus(Duration.ofHours(4)).truncatedTo(ChronoUnit.MICROS);
        EventWeatherSnapshot snapshot = new EventWeatherSnapshot(UUID.randomUUID(), eventId, staleRetrievedAt);
        snapshot.markAvailable(
                new GeocodedLocation("Old location", new GeoCoordinates(10.0, 20.0)),
                new WeatherForecast(
                        OffsetDateTime.now(), OffsetDateTime.now().plusHours(1),
                        new BigDecimal("15.0"), new BigDecimal("14.0"), 5, new BigDecimal("2.0"), "Clear sky"),
                "OPEN_METEO", staleRetrievedAt);
        snapshotRepository.save(snapshot);

        fakeWeatherProvider.nextFailure.set(new WeatherProviderException("weather provider unavailable"));

        ResponseEntity<EventWeatherResponse> response =
                restTemplate.getForEntity(url("/api/events/" + eventId + "/weather"), EventWeatherResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().resolvedLocation()).isEqualTo("Old location");
        assertThat(response.getBody().temperatureAtStart()).isEqualByComparingTo("15.0");
        assertThat(response.getBody().stale()).isTrue();
        assertThat(response.getBody().staleWarning()).isNotBlank();
        // The failed refresh attempt must not overwrite the previously saved snapshot.
        assertThat(snapshotRepository.findAll()).hasSize(1);
        assertThat(snapshotRepository.findAll().get(0).getRetrievedAt()).isEqualTo(staleRetrievedAt);
    }

    @Test
    void refreshWeather_forUnknownEvent_returns404() {
        ResponseEntity<ApiError> response = restTemplate.postForEntity(
                url("/api/events/" + UUID.randomUUID() + "/weather/refresh"), null, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void refreshWeather_withAvailableForecast_storesAndReturnsSnapshot_thenGetReturnsSameData() {
        fakeGeocodingProvider.nextLocation.set(
                new GeocodedLocation("Springfield, Illinois, United States", new GeoCoordinates(39.78, -89.65)));
        fakeWeatherProvider.nextForecast.set(new WeatherForecast(
                OffsetDateTime.now().plusDays(1), OffsetDateTime.now().plusDays(1).plusHours(3),
                new BigDecimal("21.5"), new BigDecimal("18.0"), 20, new BigDecimal("9.5"), "Partly cloudy"));

        ResponseEntity<EventWeatherResponse> refreshResponse = restTemplate.postForEntity(
                url("/api/events/" + eventId + "/weather/refresh"), null, EventWeatherResponse.class);

        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refreshResponse.getBody()).isNotNull();
        assertThat(refreshResponse.getBody().status()).isEqualTo(WeatherAvailabilityStatus.AVAILABLE);
        assertThat(refreshResponse.getBody().resolvedLocation()).isEqualTo("Springfield, Illinois, United States");
        assertThat(refreshResponse.getBody().temperatureAtStart()).isEqualByComparingTo("21.5");
        assertThat(refreshResponse.getBody().condition()).isEqualTo("Partly cloudy");

        ResponseEntity<EventWeatherResponse> getResponse =
                restTemplate.getForEntity(url("/api/events/" + eventId + "/weather"), EventWeatherResponse.class);

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).isNotNull();
        assertThat(getResponse.getBody().temperatureAtStart()).isEqualByComparingTo("21.5");
    }

    @Test
    void refreshWeather_calledTwice_replacesThePreviousSnapshotRatherThanInserting() {
        fakeGeocodingProvider.nextLocation.set(new GeocodedLocation("First", new GeoCoordinates(1.0, 1.0)));
        fakeWeatherProvider.nextForecast.set(new WeatherForecast(
                OffsetDateTime.now(), OffsetDateTime.now().plusHours(1),
                new BigDecimal("10.0"), new BigDecimal("9.0"), 5, new BigDecimal("3.0"), "Clear sky"));
        restTemplate.postForEntity(url("/api/events/" + eventId + "/weather/refresh"), null, EventWeatherResponse.class);

        fakeGeocodingProvider.nextLocation.set(new GeocodedLocation("Second", new GeoCoordinates(2.0, 2.0)));
        fakeWeatherProvider.nextForecast.set(new WeatherForecast(
                OffsetDateTime.now(), OffsetDateTime.now().plusHours(1),
                new BigDecimal("30.0"), new BigDecimal("28.0"), 60, new BigDecimal("15.0"), "Thunderstorm"));
        ResponseEntity<EventWeatherResponse> secondRefresh = restTemplate.postForEntity(
                url("/api/events/" + eventId + "/weather/refresh"), null, EventWeatherResponse.class);

        assertThat(secondRefresh.getBody()).isNotNull();
        assertThat(secondRefresh.getBody().resolvedLocation()).isEqualTo("Second");
        assertThat(secondRefresh.getBody().temperatureAtStart()).isEqualByComparingTo("30.0");
        assertThat(snapshotRepository.findAll()).hasSize(1);
    }

    @Test
    void refreshWeather_forEventBeyondForecastHorizon_returnsForecastUnavailableWithNoFabricatedValues() {
        Event distantEvent = eventRepository.save(new Event(
                UUID.randomUUID(), "Far future event", null, "123 Main St, Springfield",
                OffsetDateTime.now().plusDays(60), OffsetDateTime.now().plusDays(60).plusHours(2),
                EventSetting.INDOOR, null, Instant.now()));

        ResponseEntity<EventWeatherResponse> response = restTemplate.postForEntity(
                url("/api/events/" + distantEvent.getId() + "/weather/refresh"), null, EventWeatherResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(WeatherAvailabilityStatus.FORECAST_UNAVAILABLE);
        assertThat(response.getBody().temperatureAtStart()).isNull();
        assertThat(response.getBody().temperatureAtEnd()).isNull();
        assertThat(response.getBody().precipitationProbability()).isNull();
        assertThat(response.getBody().windSpeed()).isNull();
        assertThat(response.getBody().condition()).isNull();
        assertThat(response.getBody().message()).isNotBlank();
    }

    @Test
    void refreshWeather_withUnresolvableLocation_returns422() {
        fakeGeocodingProvider.nextFailure.set(new UnresolvableLocationException(eventId.toString()));

        ResponseEntity<ApiError> response = restTemplate.postForEntity(
                url("/api/events/" + eventId + "/weather/refresh"), null, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void refreshWeather_whenGeocodingProviderTimesOut_returns503() {
        fakeGeocodingProvider.nextFailure.set(new GeocodingProviderException("geocoding timed out"));

        ResponseEntity<ApiError> response = restTemplate.postForEntity(
                url("/api/events/" + eventId + "/weather/refresh"), null, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void refreshWeather_whenWeatherProviderFails_returns503() {
        fakeGeocodingProvider.nextLocation.set(new GeocodedLocation("Somewhere", new GeoCoordinates(1.0, 1.0)));
        fakeWeatherProvider.nextFailure.set(new WeatherProviderException("weather provider unavailable"));

        ResponseEntity<ApiError> response = restTemplate.postForEntity(
                url("/api/events/" + eventId + "/weather/refresh"), null, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @TestConfiguration
    static class FakeProvidersConfig {
        @Bean
        @Primary
        FakeGeocodingProvider fakeGeocodingProvider() {
            return new FakeGeocodingProvider();
        }

        @Bean
        @Primary
        FakeWeatherProvider fakeWeatherProvider() {
            return new FakeWeatherProvider();
        }
    }

    static class FakeGeocodingProvider implements GeocodingProvider {
        final AtomicReference<GeocodedLocation> nextLocation =
                new AtomicReference<>(new GeocodedLocation("Fake location", new GeoCoordinates(0.0, 0.0)));
        final AtomicReference<RuntimeException> nextFailure = new AtomicReference<>();

        @Override
        public GeocodedLocation geocode(String location) {
            RuntimeException failure = nextFailure.get();
            if (failure != null) {
                throw failure;
            }
            return nextLocation.get();
        }

        void reset() {
            nextLocation.set(new GeocodedLocation("Fake location", new GeoCoordinates(0.0, 0.0)));
            nextFailure.set(null);
        }
    }

    static class FakeWeatherProvider implements WeatherProvider {
        final AtomicReference<WeatherForecast> nextForecast = new AtomicReference<>(new WeatherForecast(
                OffsetDateTime.now(), OffsetDateTime.now().plusHours(1),
                new BigDecimal("20.0"), new BigDecimal("18.0"), 10, new BigDecimal("5.0"), "Clear sky"));
        final AtomicReference<RuntimeException> nextFailure = new AtomicReference<>();

        @Override
        public String name() {
            return "FAKE_PROVIDER";
        }

        @Override
        public int forecastHorizonDays() {
            return 16;
        }

        @Override
        public WeatherForecast fetchForecast(GeoCoordinates coordinates, OffsetDateTime startTime, OffsetDateTime endTime) {
            RuntimeException failure = nextFailure.get();
            if (failure != null) {
                throw failure;
            }
            return nextForecast.get();
        }

        void reset() {
            nextForecast.set(new WeatherForecast(
                    OffsetDateTime.now(), OffsetDateTime.now().plusHours(1),
                    new BigDecimal("20.0"), new BigDecimal("18.0"), 10, new BigDecimal("5.0"), "Clear sky"));
            nextFailure.set(null);
        }
    }
}
