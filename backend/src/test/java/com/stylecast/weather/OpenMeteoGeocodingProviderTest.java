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
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link OpenMeteoGeocodingProvider}.
 *
 * <p>Response-normalization behavior ({@link OpenMeteoGeocodingProvider#parseResponse})
 * is tested directly against hand-built JSON fixtures shaped like the
 * Open-Meteo Geocoding API. HTTP-layer behavior (timeout, error status) is
 * tested against a local {@link HttpServer} fake - never the real Open-Meteo
 * API.
 */
class OpenMeteoGeocodingProviderTest {

    private static final JsonMapper MAPPER = new JsonMapper();

    private HttpServer fakeServer;

    @AfterEach
    void stopFakeServer() {
        if (fakeServer != null) {
            fakeServer.stop(0);
        }
    }

    private WeatherProperties properties(String geocodingBaseUrl, long readTimeoutMs) {
        return new WeatherProperties(geocodingBaseUrl, "http://localhost", 1000, readTimeoutMs, 16, 180, "OPEN_METEO");
    }

    private OpenMeteoGeocodingProvider providerWithoutHttp() {
        return new OpenMeteoGeocodingProvider(properties("http://localhost", 1000), WebClient.builder());
    }

    // --- parseResponse: pure response-normalization logic -------------------

    @Test
    void parseResponse_withValidResult_returnsGeocodedLocation() {
        JsonNode response = MAPPER.readTree("""
                {
                  "results": [
                    {"name": "New York", "latitude": 40.7143, "longitude": -74.006, "admin1": "New York", "country": "United States"}
                  ]
                }
                """);

        GeocodedLocation location = providerWithoutHttp().parseResponse(response, "New York");

        assertThat(location.resolvedName()).isEqualTo("New York, New York, United States");
        assertThat(location.coordinates().latitude()).isEqualTo(40.7143);
        assertThat(location.coordinates().longitude()).isEqualTo(-74.006);
    }

    @Test
    void parseResponse_withNoResults_throwsUnresolvableLocation() {
        JsonNode response = MAPPER.readTree("{ \"results\": [] }");

        assertThatThrownBy(() -> providerWithoutHttp().parseResponse(response, "Nowhereville"))
                .isInstanceOf(UnresolvableLocationException.class)
                .hasMessageContaining("Nowhereville");
    }

    @Test
    void parseResponse_withMissingResultsField_throwsUnresolvableLocation() {
        JsonNode response = MAPPER.readTree("{}");

        assertThatThrownBy(() -> providerWithoutHttp().parseResponse(response, "Nowhereville"))
                .isInstanceOf(UnresolvableLocationException.class);
    }

    @Test
    void parseResponse_withMissingCoordinates_throwsGeocodingProviderException() {
        JsonNode response = MAPPER.readTree("{ \"results\": [ {\"name\": \"Somewhere\"} ] }");

        assertThatThrownBy(() -> providerWithoutHttp().parseResponse(response, "Somewhere"))
                .isInstanceOf(GeocodingProviderException.class);
    }

    @Test
    void parseResponse_withNullResponse_throwsGeocodingProviderException() {
        assertThatThrownBy(() -> providerWithoutHttp().parseResponse(null, "Anywhere"))
                .isInstanceOf(GeocodingProviderException.class);
    }

    // --- HTTP-layer behavior: fake local server, never the real API --------

    @Test
    void geocode_withSuccessfulHttpResponse_returnsGeocodedLocation() throws IOException {
        fakeServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        fakeServer.createContext("/v1/search", exchange -> {
            byte[] body = "{\"results\": [{\"name\": \"Paris\", \"latitude\": 48.8566, \"longitude\": 2.3522, \"country\": \"France\"}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        fakeServer.start();

        OpenMeteoGeocodingProvider provider = new OpenMeteoGeocodingProvider(
                properties("http://localhost:" + fakeServer.getAddress().getPort(), 1000), WebClient.builder());

        GeocodedLocation location = provider.geocode("Paris");

        assertThat(location.resolvedName()).isEqualTo("Paris, France");
        assertThat(location.coordinates().latitude()).isEqualTo(48.8566);
    }

    @Test
    void geocode_withErrorStatus_throwsGeocodingProviderException() throws IOException {
        fakeServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        fakeServer.createContext("/v1/search", exchange -> {
            byte[] body = "Internal Server Error".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        fakeServer.start();

        OpenMeteoGeocodingProvider provider = new OpenMeteoGeocodingProvider(
                properties("http://localhost:" + fakeServer.getAddress().getPort(), 1000), WebClient.builder());

        assertThatThrownBy(() -> provider.geocode("Anywhere"))
                .isInstanceOf(GeocodingProviderException.class);
    }

    @Test
    void geocode_withSlowServer_timesOutAndThrowsGeocodingProviderException() throws IOException {
        fakeServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        fakeServer.createContext("/v1/search", exchange -> {
            try {
                TimeUnit.MILLISECONDS.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        fakeServer.start();

        OpenMeteoGeocodingProvider provider = new OpenMeteoGeocodingProvider(
                properties("http://localhost:" + fakeServer.getAddress().getPort(), 100), WebClient.builder());

        assertThatThrownBy(() -> provider.geocode("Anywhere"))
                .isInstanceOf(GeocodingProviderException.class);
    }
}
