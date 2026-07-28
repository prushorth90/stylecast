package com.stylecast.event.dto;

import com.stylecast.event.Event;
import com.stylecast.event.EventSetting;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Public API representation of an {@link Event}.
 */
public record EventResponse(
        UUID id,
        String title,
        String description,
        String location,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        EventSetting setting,
        String dressCode,
        Instant createdAt
) {
    public static EventResponse fromEntity(Event event) {
        return new EventResponse(
                event.getId(),
                event.getTitle(),
                event.getDescription(),
                event.getLocation(),
                event.getStartTime(),
                event.getEndTime(),
                event.getSetting(),
                event.getDressCode(),
                event.getCreatedAt());
    }
}
