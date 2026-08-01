package com.stylecast.event;

import com.stylecast.event.dto.CreateEventRequest;
import com.stylecast.event.dto.EventResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Thin controller for manual event creation, upcoming-event listing, and
 * event retrieval. All business rules live in {@link EventService}.
 */
@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    public ResponseEntity<EventResponse> createEvent(@Valid @RequestBody CreateEventRequest request) {
        EventResponse response = eventService.createEvent(request);
        return ResponseEntity.created(URI.create("/api/events/" + response.id())).body(response);
    }

    @GetMapping
    public List<EventResponse> listUpcomingEvents() {
        return eventService.listUpcomingEvents();
    }

    /** Every one of the current user's events (past and upcoming) - backs the saved history page. */
    @GetMapping("/history")
    public List<EventResponse> listEventHistory() {
        return eventService.listEventHistory();
    }

    @GetMapping("/{eventId}")
    public EventResponse getEvent(@PathVariable UUID eventId) {
        return eventService.getEvent(eventId);
    }

    @PutMapping("/{eventId}")
    public EventResponse updateEvent(@PathVariable UUID eventId, @Valid @RequestBody CreateEventRequest request) {
        return eventService.updateEvent(eventId, request);
    }
}
