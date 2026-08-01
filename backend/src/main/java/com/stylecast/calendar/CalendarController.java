package com.stylecast.calendar;

import com.stylecast.calendar.dto.CalendarEventResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Thin controller for the custom StyleCast calendar (Task 18). All business
 * rules live in {@link CalendarEventService}.
 */
@RestController
@RequestMapping("/api/events")
public class CalendarController {

    private final CalendarEventService calendarEventService;

    public CalendarController(CalendarEventService calendarEventService) {
        this.calendarEventService = calendarEventService;
    }

    /**
     * Every one of the current user's events that overlaps {@code [start,
     * end)}, ordered by start time - see {@link
     * CalendarEventService#getEventsInRange}.
     */
    @GetMapping("/calendar")
    public List<CalendarEventResponse> getCalendarEvents(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime end) {
        return calendarEventService.getEventsInRange(start, end);
    }
}
