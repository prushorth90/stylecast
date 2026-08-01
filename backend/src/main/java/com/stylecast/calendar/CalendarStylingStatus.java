package com.stylecast.calendar;

/**
 * Presentation-safe summary of how far an event has progressed through the
 * styling workflow, derived purely from persisted data (never from an
 * in-memory job registry) so the same event always reports the same status
 * regardless of which application instance answers the request:
 *
 * <ol>
 *   <li>{@link #EVENT_ONLY} - no saved styling preferences yet.</li>
 *   <li>{@link #PREFERENCES_SET} - preferences saved, no occasion
 *       interpretation generated yet.</li>
 *   <li>{@link #INTERPRETATION_READY} - interpretation exists, but live
 *       recommendations have never been generated for this event
 *       (generation 0).</li>
 *   <li>{@link #RECOMMENDATIONS_PENDING} - a generation was attempted but
 *       every required category/item came back empty or the provider was
 *       unavailable (a generation row exists, but there is nothing usable
 *       to show yet).</li>
 *   <li>{@link #RECOMMENDATIONS_READY} - the latest generation has usable
 *       (complete or partial) results and is not stale.</li>
 *   <li>{@link #RECOMMENDATIONS_STALE} - the latest generation exists but
 *       the event's preferences/interpretation changed since it was
 *       produced.</li>
 * </ol>
 */
public enum CalendarStylingStatus {
    EVENT_ONLY,
    PREFERENCES_SET,
    INTERPRETATION_READY,
    RECOMMENDATIONS_PENDING,
    RECOMMENDATIONS_READY,
    RECOMMENDATIONS_STALE
}
