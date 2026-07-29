package com.stylecast.weather;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link OpenMeteoWeatherProvider}.
 *
 * <p>Response-normalization behavior ({@link OpenMeteoWeatherProvider#parseResponse})
 * is tested directly against hand-built JSON fixtures shaped like the
 * Open-Meteo Forecast API. HTTP-layer behavior (timeout, error status) is
 * tested against a local {@link HttpServer} fake - never the real Open-Meteo
 * API.
 */
class OpenMeteoWeatherProviderTest {

    private static final JsonMapper MAPPER = new JsonMapper();

    private static final String HOURLY_FIXTURE = """
            {
              "hourly": {
                "time": [
                  "2026-08-01T17:00", "2026-08-01T18:00", "2026-08-01T19:00",
                  "2026-08-01T20:00", "2026-08-01T21:00", "2026-08-01T22:00"
                ],
                "temperature_2m": [19.0, 18.5, 18.0, 17.5, 17.0, 16.5],
                "precipitation_probability": [5, 10, 15, 20, 25, 30],
                "wind_speed_10m": [10.0, 10.5, 11.0, 11.5, 12.0, 12.5],
                "weathercode": [1, 1, 2, 2, 3, 3]
              }
            }
            """;

    private HttpServer fakeServer;

    @AfterEach
    void stopFakeServer() {
        if (fakeServer != null) {
            fakeServer.stop(0);
        }
    }

    private WeatherProperties properties(String forecastBaseUrl, long readTimeoutMs) {
        return new WeatherProperties("http://localhost", forecastBaseUrl, 1000, readTimeoutMs, 16, 180, "OPEN_METEO");
    }

    private OpenMeteoWeatherProvider providerWithoutHttp() {
        return new OpenMeteoWeatherProvider(properties("http://localhost", 1000), WebClient.builder());
    }

    // --- parseResponse: pure response-normalization logic -------------------

    @Test
    void parseResponse_withExactHourMatches_extractsStartAndEndReadings() {
        JsonNode response = MAPPER.readTree(HOURLY_FIXTURE);
        OffsetDateTime start = OffsetDateTime.of(2026, 8, 1, 18, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime end = OffsetDateTime.of(2026, 8, 1, 21, 0, 0, 0, ZoneOffset.UTC);

        WeatherForecast forecast = providerWithoutHttp().parseResponse(response, start, end);

        assertThat(forecast.forecastStart()).isEqualTo(start);
        assertThat(forecast.forecastEnd()).isEqualTo(end);
        assertThat(forecast.temperatureAtStart()).isEqualByComparingTo("18.5");
        assertThat(forecast.temperatureAtEnd()).isEqualByComparingTo("17.0");
        assertThat(forecast.precipitationProbability()).isEqualTo(10);
        assertThat(forecast.windSpeed()).isEqualByComparingTo("10.5");
        assertThat(forecast.condition()).isEqualTo("Mainly clear");
    }

    @Test
    void parseResponse_withNearestHourMatch_picksClosestReading() {
        JsonNode response = MAPPER.readTree(HOURLY_FIXTURE);
        // 18:20 is closer to 18:00 than 19:00.
        OffsetDateTime start = OffsetDateTime.of(2026, 8, 1, 18, 20, 0, 0, ZoneOffset.UTC);
        OffsetDateTime end = OffsetDateTime.of(2026, 8, 1, 20, 50, 0, 0, ZoneOffset.UTC);

        WeatherForecast forecast = providerWithoutHttp().parseResponse(response, start, end);

        assertThat(forecast.forecastStart()).isEqualTo(OffsetDateTime.of(2026, 8, 1, 18, 0, 0, 0, ZoneOffset.UTC));
        assertThat(forecast.forecastEnd()).isEqualTo(OffsetDateTime.of(2026, 8, 1, 21, 0, 0, 0, ZoneOffset.UTC));
    }

    @Test
    void parseResponse_withUnknownWeatherCode_fallsBackToGenericDescription() {
        JsonNode response = MAPPER.readTree("""
                {
                  "hourly": {
                    "time": ["2026-08-01T18:00"],
                    "temperature_2m": [18.5],
                    "precipitation_probability": [10],
                    "wind_speed_10m": [10.5],
                    "weathercode": [123]
                  }
                }
                """);
        OffsetDateTime instant = OffsetDateTime.of(2026, 8, 1, 18, 0, 0, 0, ZoneOffset.UTC);

        WeatherForecast forecast = providerWithoutHttp().parseResponse(response, instant, instant);

        assertThat(forecast.condition()).isEqualTo("Unknown conditions");
    }

    @Test
    void parseResponse_withNoHourlyData_throwsWeatherProviderException() {
        JsonNode response = MAPPER.readTree("{ \"hourly\": { \"time\": [] } }");
        OffsetDateTime instant = OffsetDateTime.of(2026, 8, 1, 18, 0, 0, 0, ZoneOffset.UTC);

        assertThatThrownBy(() -> providerWithoutHttp().parseResponse(response, instant, instant))
                .isInstanceOf(WeatherProviderException.class);
    }

    @Test
    void parseResponse_withNullResponse_throwsWeatherProviderException() {
        OffsetDateTime instant = OffsetDateTime.of(2026, 8, 1, 18, 0, 0, 0, ZoneOffset.UTC);

        assertThatThrownBy(() -> providerWithoutHttp().parseResponse(null, instant, instant))
                .isInstanceOf(WeatherProviderException.class);
    }

    @Test
    void forecastHorizonDays_returnsConfiguredValue() {
        assertThat(providerWithoutHttp().forecastHorizonDays()).isEqualTo(16);
        assertThat(providerWithoutHttp().name()).isEqualTo("OPEN_METEO");
    }

    // --- HTTP-layer behavior: fake local server, never the real API --------

    @Test
    void fetchForecast_withSuccessfulHttpResponse_returnsForecast() throws IOException {
        fakeServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        fakeServer.createContext("/v1/forecast", exchange -> {
            byte[] body = HOURLY_FIXTURE.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        fakeServer.start();

        OpenMeteoWeatherProvider provider = new OpenMeteoWeatherProvider(
                properties("http://localhost:" + fakeServer.getAddress().getPort(), 1000), WebClient.builder());

        OffsetDateTime start = OffsetDateTime.of(2026, 8, 1, 18, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime end = OffsetDateTime.of(2026, 8, 1, 21, 0, 0, 0, ZoneOffset.UTC);
        WeatherForecast forecast = provider.fetchForecast(new GeoCoordinates(40.7, -74.0), start, end);

        assertThat(forecast.temperatureAtStart()).isEqualByComparingTo("18.5");
    }

    @Test
    void fetchForecast_withErrorStatus_throwsWeatherProviderException() throws IOException {
        fakeServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        fakeServer.createContext("/v1/forecast", exchange -> {
            byte[] body = "Internal Server Error".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        fakeServer.start();

        OpenMeteoWeatherProvider provider = new OpenMeteoWeatherProvider(
                properties("http://localhost:" + fakeServer.getAddress().getPort(), 1000), WebClient.builder());

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        assertThatThrownBy(() -> provider.fetchForecast(new GeoCoordinates(40.7, -74.0), now, now.plusHours(2)))
                .isInstanceOf(WeatherProviderException.class);
    }

    @Test
    void fetchForecast_withSlowServer_timesOutAndThrowsWeatherProviderException() throws IOException {
        fakeServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        fakeServer.createContext("/v1/forecast", exchange -> {
            try {
                TimeUnit.MILLISECONDS.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] body = HOURLY_FIXTURE.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        fakeServer.start();

        OpenMeteoWeatherProvider provider = new OpenMeteoWeatherProvider(
                properties("http://localhost:" + fakeServer.getAddress().getPort(), 100), WebClient.builder());

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        assertThatThrownBy(() -> provider.fetchForecast(new GeoCoordinates(40.7, -74.0), now, now.plusHours(2)))
                .isInstanceOf(WeatherProviderException.class);
    }
}
