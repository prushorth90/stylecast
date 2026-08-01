package com.stylecast.calendar;

/**
 * Thrown when {@code GET /api/events/calendar}'s {@code start}/{@code end}
 * query parameters violate a validation rule (start not before end, or the
 * requested range exceeds {@link CalendarEventService#MAX_RANGE}). Mapped to
 * HTTP 400 by the global exception handler.
 */
public class InvalidCalendarRangeException extends RuntimeException {

    public InvalidCalendarRangeException(String message) {
        super(message);
    }
}
