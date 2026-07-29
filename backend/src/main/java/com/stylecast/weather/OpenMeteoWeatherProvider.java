package com.stylecast.weather;

import io.netty.channel.ChannelOption;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link WeatherProvider} backed by
 * <a href="https://open-meteo.com/en/docs">Open-Meteo's free Forecast
 * API</a>, which requires no API key. Requests hourly data explicitly in
 * UTC so matching a forecast reading to the event's start/end instants
 * never depends on the event location's local time zone.
 *
 * <p>{@code precipitationProbability}, {@code windSpeed}, and
 * {@code condition} are read from the hourly reading closest to the event's
 * start instant (a single representative reading for the whole window,
 * rather than separate start/end values, matching the snapshot schema).
 */
@Component
public class OpenMeteoWeatherProvider implements WeatherProvider {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final WeatherProperties properties;
    private final WebClient webClient;

    public OpenMeteoWeatherProvider(WeatherProperties properties, WebClient.Builder webClientBuilder) {
        this.properties = properties;

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(properties.connectTimeoutMs()))
                .responseTimeout(Duration.ofMillis(properties.readTimeoutMs()));

        this.webClient = webClientBuilder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .baseUrl(properties.forecastBaseUrl())
                .build();
    }

    @Override
    public String name() {
        return properties.providerName();
    }

    @Override
    public int forecastHorizonDays() {
        return properties.forecastHorizonDays();
    }

    @Override
    public WeatherForecast fetchForecast(GeoCoordinates coordinates, OffsetDateTime startTime, OffsetDateTime endTime) {
        OffsetDateTime utcStart = startTime.withOffsetSameInstant(ZoneOffset.UTC);
        OffsetDateTime utcEnd = endTime.withOffsetSameInstant(ZoneOffset.UTC);

        JsonNode response;
        try {
            response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/forecast")
                            .queryParam("latitude", coordinates.latitude())
                            .queryParam("longitude", coordinates.longitude())
                            .queryParam("hourly", "temperature_2m,precipitation_probability,wind_speed_10m,weathercode")
                            .queryParam("timezone", "UTC")
                            .queryParam("start_date", utcStart.toLocalDate().format(DATE_FORMATTER))
                            .queryParam("end_date", utcEnd.toLocalDate().format(DATE_FORMATTER))
                            .build())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
        } catch (WebClientResponseException ex) {
            throw new WeatherProviderException(
                    "Weather provider returned an error status: " + ex.getStatusCode(), ex);
        } catch (WebClientRequestException ex) {
            throw new WeatherProviderException("Weather provider request failed: " + ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            throw new WeatherProviderException("Weather provider call failed: " + ex.getMessage(), ex);
        }

        return parseResponse(response, utcStart, utcEnd);
    }

    WeatherForecast parseResponse(JsonNode response, OffsetDateTime targetStart, OffsetDateTime targetEnd) {
        if (response == null) {
            throw new WeatherProviderException("Weather provider returned an empty response");
        }

        JsonNode hourly = response.path("hourly");
        List<OffsetDateTime> timestamps = parseTimestamps(hourly.path("time"));
        if (timestamps.isEmpty()) {
            throw new WeatherProviderException("Weather provider response has no hourly forecast data");
        }

        int startIndex = closestIndex(timestamps, targetStart);
        int endIndex = closestIndex(timestamps, targetEnd);

        BigDecimal temperatureAtStart = decimalAt(hourly.path("temperature_2m"), startIndex);
        BigDecimal temperatureAtEnd = decimalAt(hourly.path("temperature_2m"), endIndex);
        Integer precipitationProbability = intAt(hourly.path("precipitation_probability"), startIndex);
        BigDecimal windSpeed = decimalAt(hourly.path("wind_speed_10m"), startIndex);
        String condition = OpenMeteoWeatherCodes.describe(intAt(hourly.path("weathercode"), startIndex));

        return new WeatherForecast(
                timestamps.get(startIndex),
                timestamps.get(endIndex),
                temperatureAtStart,
                temperatureAtEnd,
                precipitationProbability,
                windSpeed,
                condition);
    }

    private List<OffsetDateTime> parseTimestamps(JsonNode timeArray) {
        List<OffsetDateTime> timestamps = new ArrayList<>();
        if (!timeArray.isArray()) {
            return timestamps;
        }
        for (JsonNode node : timeArray) {
            timestamps.add(LocalDateTime.parse(node.asString()).atOffset(ZoneOffset.UTC));
        }
        return timestamps;
    }

    private int closestIndex(List<OffsetDateTime> timestamps, OffsetDateTime target) {
        int closest = 0;
        long smallestDiffSeconds = Long.MAX_VALUE;
        for (int i = 0; i < timestamps.size(); i++) {
            long diffSeconds = Math.abs(Duration.between(timestamps.get(i), target).toSeconds());
            if (diffSeconds < smallestDiffSeconds) {
                smallestDiffSeconds = diffSeconds;
                closest = i;
            }
        }
        return closest;
    }

    private BigDecimal decimalAt(JsonNode array, int index) {
        if (!array.isArray() || index >= array.size()) {
            return null;
        }
        JsonNode value = array.get(index);
        if (value == null || value.isNull()) {
            return null;
        }
        return BigDecimal.valueOf(value.asDouble());
    }

    private Integer intAt(JsonNode array, int index) {
        if (!array.isArray() || index >= array.size()) {
            return null;
        }
        JsonNode value = array.get(index);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asInt();
    }
}
