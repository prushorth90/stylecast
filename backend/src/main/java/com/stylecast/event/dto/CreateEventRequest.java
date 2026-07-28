package com.stylecast.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.stylecast.event.EventSetting;

import java.time.OffsetDateTime;

/**
 * Request body for {@code POST /api/events}.
 *
 * Cross-field validation (end time after start time) is enforced in the
 * service layer rather than here, since it depends on domain rules rather
 * than simple per-field constraints.
 */
public record CreateEventRequest(
        @NotBlank(message = "must not be blank")
        @Size(max = 200, message = "must be at most 200 characters")
        String title,

        @Size(max = 2000, message = "must be at most 2000 characters")
        String description,

        @NotBlank(message = "must not be blank")
        @Size(max = 300, message = "must be at most 300 characters")
        String location,

        @NotNull(message = "must not be null")
        OffsetDateTime startTime,

        @NotNull(message = "must not be null")
        OffsetDateTime endTime,

        @NotNull(message = "must not be null")
        EventSetting setting,

        @Size(max = 100, message = "must be at most 100 characters")
        String dressCode
) {
}
