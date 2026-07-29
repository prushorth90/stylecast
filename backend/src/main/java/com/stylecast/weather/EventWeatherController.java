package com.stylecast.weather;

import com.stylecast.weather.dto.EventWeatherResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Thin controller for event-time weather. All business rules live in
 * {@link EventWeatherService}.
 */
@RestController
@RequestMapping("/api/events/{eventId}/weather")
public class EventWeatherController {

    private final EventWeatherService weatherService;

    public EventWeatherController(EventWeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @GetMapping
    public EventWeatherResponse getWeather(@PathVariable UUID eventId) {
        return weatherService.getWeather(eventId);
    }

    @PostMapping("/refresh")
    public EventWeatherResponse refreshWeather(@PathVariable UUID eventId) {
        return weatherService.refreshWeather(eventId);
    }
}
