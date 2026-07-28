package com.stylecast.event.styling;

import com.stylecast.event.styling.dto.EventStylePreferencesResponse;
import com.stylecast.event.styling.dto.UpsertEventStylePreferencesRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Thin controller for event styling preferences. All business rules live in
 * {@link EventStylePreferencesService}.
 */
@RestController
@RequestMapping("/api/events/{eventId}/preferences")
public class EventStylePreferencesController {

    private final EventStylePreferencesService preferencesService;

    public EventStylePreferencesController(EventStylePreferencesService preferencesService) {
        this.preferencesService = preferencesService;
    }

    @GetMapping
    public EventStylePreferencesResponse getPreferences(@PathVariable UUID eventId) {
        return preferencesService.getPreferences(eventId);
    }

    @PutMapping
    public EventStylePreferencesResponse upsertPreferences(
            @PathVariable UUID eventId, @Valid @RequestBody UpsertEventStylePreferencesRequest request) {
        return preferencesService.upsertPreferences(eventId, request);
    }
}
