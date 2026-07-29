package com.stylecast.weather;

import io.netty.channel.ChannelOption;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;
import tools.jackson.databind.JsonNode;

import java.time.Duration;

/**
 * {@link GeocodingProvider} backed by
 * <a href="https://open-meteo.com/en/docs/geocoding-api">Open-Meteo's free
 * Geocoding API</a>, which requires no API key.
 */
@Component
public class OpenMeteoGeocodingProvider implements GeocodingProvider {

    private final WebClient webClient;

    public OpenMeteoGeocodingProvider(WeatherProperties properties, WebClient.Builder webClientBuilder) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(properties.connectTimeoutMs()))
                .responseTimeout(Duration.ofMillis(properties.readTimeoutMs()));

        this.webClient = webClientBuilder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .baseUrl(properties.geocodingBaseUrl())
                .build();
    }

    @Override
    public GeocodedLocation geocode(String location) {
        JsonNode response;
        try {
            response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/search")
                            .queryParam("name", location)
                            .queryParam("count", 1)
                            .build())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
        } catch (WebClientResponseException ex) {
            throw new GeocodingProviderException(
                    "Geocoding provider returned an error status: " + ex.getStatusCode(), ex);
        } catch (WebClientRequestException ex) {
            throw new GeocodingProviderException("Geocoding provider request failed: " + ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            throw new GeocodingProviderException("Geocoding provider call failed: " + ex.getMessage(), ex);
        }

        return parseResponse(response, location);
    }

    GeocodedLocation parseResponse(JsonNode response, String location) {
        if (response == null) {
            throw new GeocodingProviderException("Geocoding provider returned an empty response");
        }

        JsonNode results = response.path("results");
        if (!results.isArray() || results.isEmpty()) {
            throw new UnresolvableLocationException(location);
        }

        JsonNode first = results.get(0);
        if (!first.has("latitude") || !first.has("longitude")) {
            throw new GeocodingProviderException("Geocoding provider result is missing coordinates");
        }

        double latitude = first.path("latitude").asDouble();
        double longitude = first.path("longitude").asDouble();
        String resolvedName = buildResolvedName(first);

        return new GeocodedLocation(resolvedName, new GeoCoordinates(latitude, longitude));
    }

    private String buildResolvedName(JsonNode result) {
        StringBuilder builder = new StringBuilder();
        appendPart(builder, textOrNull(result, "name"));
        appendPart(builder, textOrNull(result, "admin1"));
        appendPart(builder, textOrNull(result, "country"));
        return builder.isEmpty() ? "Unknown location" : builder.toString();
    }

    private void appendPart(StringBuilder builder, String part) {
        if (part == null || part.isBlank()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append(", ");
        }
        builder.append(part);
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return value.asString();
    }
}
