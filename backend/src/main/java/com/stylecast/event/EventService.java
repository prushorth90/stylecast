package com.stylecast.event;

import com.stylecast.auth.CurrentUserProvider;
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
 *
 * Every method that loads an existing event resolves it through {@link
 * EventRepository#findByIdAndUserId} using {@link CurrentUserProvider} -
 * never a userId supplied by the caller - so a user can never read, edit,
 * or list another user's events, including by guessing a UUID (an
 * event that exists but isn't owned by the caller looks identical to one
 * that doesn't exist at all: {@link EventNotFoundException} / 404).
 */
@Service
public class EventService {

    private final EventRepository eventRepository;
    private final CurrentUserProvider currentUserProvider;

    public EventService(EventRepository eventRepository, CurrentUserProvider currentUserProvider) {
        this.eventRepository = eventRepository;
        this.currentUserProvider = currentUserProvider;
    }

    public EventResponse createEvent(CreateEventRequest request) {
        if (!request.endTime().isAfter(request.startTime())) {
            throw new InvalidEventException("endTime must be after startTime");
        }

        Event event = new Event(
                UUID.randomUUID(),
                currentUserProvider.requireCurrentUserId(),
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

    /**
     * Updates an existing event's fields in place (never creates a new
     * row) - used when the "Continue" action in the two-step event setup
     * flow re-submits Step 1 for an event that was already saved (e.g.
     * after clicking "Back" from Step 2), so repeated edits never create
     * duplicate events.
     */
    public EventResponse updateEvent(UUID eventId, CreateEventRequest request) {
        if (!request.endTime().isAfter(request.startTime())) {
            throw new InvalidEventException("endTime must be after startTime");
        }

        Event event = eventRepository.findByIdAndUserId(eventId, currentUserProvider.requireCurrentUserId())
                .orElseThrow(() -> new EventNotFoundException(eventId));

        event.update(
                request.title(),
                request.description(),
                request.location(),
                request.startTime(),
                request.endTime(),
                request.setting(),
                request.dressCode());

        Event saved = eventRepository.save(event);
        return EventResponse.fromEntity(saved);
    }

    public List<EventResponse> listUpcomingEvents() {
        return eventRepository
                .findByUserIdAndEndTimeAfterOrderByStartTimeAsc(currentUserProvider.requireCurrentUserId(), OffsetDateTime.now())
                .stream()
                .map(EventResponse::fromEntity)
                .toList();
    }

    /**
     * All of the current user's events (past and upcoming), most recent
     * first - backs the saved event/look history page.
     */
    public List<EventResponse> listEventHistory() {
        return eventRepository.findByUserIdOrderByStartTimeDesc(currentUserProvider.requireCurrentUserId())
                .stream()
                .map(EventResponse::fromEntity)
                .toList();
    }

    public EventResponse getEvent(UUID eventId) {
        Event event = eventRepository.findByIdAndUserId(eventId, currentUserProvider.requireCurrentUserId())
                .orElseThrow(() -> new EventNotFoundException(eventId));
        return EventResponse.fromEntity(event);
    }

    /**
     * Deletes an event the caller owns. Cascades to every per-event child
     * table (preferences, weather snapshot, occasion interpretation, local
     * and live recommendations) via each table's existing {@code ON DELETE
     * CASCADE} foreign key to {@code events.id} - no explicit cleanup is
     * needed here.
     */
    public void deleteEvent(UUID eventId) {
        Event event = eventRepository.findByIdAndUserId(eventId, currentUserProvider.requireCurrentUserId())
                .orElseThrow(() -> new EventNotFoundException(eventId));
        eventRepository.delete(event);
    }
}

