package com.stylecast.calendar.dto;

import com.stylecast.calendar.CalendarStylingStatus;
import com.stylecast.event.EventSetting;
import com.stylecast.recommendation.LiveRecommendationCompleteness;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Public API representation of one event as shown on the custom StyleCast
 * calendar ({@code GET /api/events/calendar}). Deliberately narrower than
 * {@link com.stylecast.event.dto.EventResponse}: it omits {@code
 * description}/{@code createdAt} (not needed for calendar rendering) and
 * adds calendar-specific presentation fields instead.
 *
 * @param timezone the literal UTC-offset id (e.g. {@code "Z"}, {@code
 *                 "+05:00"}) of the persisted {@code start} instant - the
 *                 {@code Event} model has no separate IANA time zone
 *                 concept, so this reflects the exact offset the event was
 *                 stored with rather than any per-viewer conversion (the
 *                 same event always reports the same {@code timezone} to
 *                 every caller).
 * @param allDay   {@code true} when {@code start}/{@code end} both fall on
 *                 an exact midnight boundary (in the offset the event was
 *                 stored with) and span at least one full day - a
 *                 best-effort, deterministic heuristic since the {@code
 *                 Event} model has no explicit all-day flag.
 * @param recommendationStatus the latest live-recommendation generation's
 *                 raw completeness, or {@code null} when nothing has been
 *                 generated yet (generation 0).
 * @param canEdit  always {@code true} today (the endpoint only ever returns
 *                 the caller's own events) - kept explicit so the frontend
 *                 never needs to assume ownership from the mere presence of
 *                 an event in this response.
 */
public record CalendarEventResponse(
        UUID id,
        String title,
        OffsetDateTime start,
        OffsetDateTime end,
        String timezone,
        boolean allDay,
        String location,
        EventSetting setting,
        String dressCode,
        CalendarStylingStatus stylingStatus,
        LiveRecommendationCompleteness recommendationStatus,
        boolean stale,
        boolean canEdit) {
}
