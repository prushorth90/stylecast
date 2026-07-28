package com.stylecast.event;

import com.stylecast.event.dto.CreateEventRequest;
import com.stylecast.event.dto.EventResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Application service for manual event creation and retrieval. Holds the
 * domain rules that don't belong in the controller or in simple per-field
 * bean validation.
 */
@Service
public class EventService {

    private final EventRepository eventRepository;

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public EventResponse createEvent(CreateEventRequest request) {
        if (!request.endTime().isAfter(request.startTime())) {
            throw new InvalidEventException("endTime must be after startTime");
        }

        Event event = new Event(
                UUID.randomUUID(),
                request.title(),
                request.description(),
                request.location(),
                request.startTime(),
                request.endTime(),
                request.setting(),
                request.dressCode(),
                Instant.now());

        Event saved = eventRepository.save(event);
        return EventResponse.fromEntity(saved);
    }

    public List<EventResponse> listUpcomingEvents() {
        return eventRepository.findByEndTimeAfterOrderByStartTimeAsc(OffsetDateTime.now())
                .stream()
                .map(EventResponse::fromEntity)
                .toList();
    }

    public EventResponse getEvent(UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
        return EventResponse.fromEntity(event);
    }
}
