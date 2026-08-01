package com.stylecast.calendar;

import com.stylecast.auth.CurrentUserProvider;
import com.stylecast.calendar.dto.CalendarEventResponse;
import com.stylecast.event.Event;
import com.stylecast.event.EventRepository;
import com.stylecast.event.styling.EventStylePreferencesRepository;
import com.stylecast.occasion.OccasionInterpretationRepository;
import com.stylecast.recommendation.LiveOutfitRecommendation;
import com.stylecast.recommendation.LiveOutfitRecommendationRepository;
import com.stylecast.recommendation.LiveRecommendationCompleteness;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Application service backing the custom StyleCast calendar (Task 18).
 * Read-only: never generates an occasion interpretation, never triggers a
 * live-recommendation search - every field it returns comes from data that
 * already exists, so simply viewing the calendar never has side effects or
 * incurs AI/provider cost.
 *
 * <p>Lives in its own top-level module (rather than inside {@code
 * com.stylecast.event}) because it aggregates across {@code event}, {@code
 * occasion}, and {@code recommendation} - keeping that fan-out dependency
 * here (calendar depends on the others) avoids inverting any of those
 * modules' existing dependency directions.
 */
@Service
public class CalendarEventService {

    /**
     * Upper bound on {@code end - start} for one calendar request - keeps a
     * single query bounded and prevents a client from requesting "every
     * historical event ever" through this endpoint (use {@code GET
     * /api/events/history} for that). Generous enough for a year-spanning
     * month-view prev/next chain (12 months plus padding weeks).
     */
    static final Duration MAX_RANGE = Duration.ofDays(400);

    private final EventRepository eventRepository;
    private final EventStylePreferencesRepository preferencesRepository;
    private final OccasionInterpretationRepository interpretationRepository;
    private final LiveOutfitRecommendationRepository liveRecommendationRepository;
    private final CurrentUserProvider currentUserProvider;

    public CalendarEventService(
            EventRepository eventRepository,
            EventStylePreferencesRepository preferencesRepository,
            OccasionInterpretationRepository interpretationRepository,
            LiveOutfitRecommendationRepository liveRecommendationRepository,
            CurrentUserProvider currentUserProvider) {
        this.eventRepository = eventRepository;
        this.preferencesRepository = preferencesRepository;
        this.interpretationRepository = interpretationRepository;
        this.liveRecommendationRepository = liveRecommendationRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional(readOnly = true)
    public List<CalendarEventResponse> getEventsInRange(OffsetDateTime start, OffsetDateTime end) {
        if (start == null || end == null) {
            throw new InvalidCalendarRangeException("start and end are required");
        }
        if (!start.isBefore(end)) {
            throw new InvalidCalendarRangeException("start must be before end");
        }
        if (Duration.between(start, end).compareTo(MAX_RANGE) > 0) {
            throw new InvalidCalendarRangeException(
                    "Requested range exceeds the maximum of " + MAX_RANGE.toDays() + " days");
        }

        UUID userId = currentUserProvider.requireCurrentUserId();
        List<Event> events = eventRepository
                .findByUserIdAndStartTimeLessThanAndEndTimeGreaterThanOrderByStartTimeAsc(userId, end, start);
        if (events.isEmpty()) {
            return List.of();
        }

        List<UUID> eventIds = events.stream().map(Event::getId).toList();

        Set<UUID> eventIdsWithPreferences = new HashSet<>();
        preferencesRepository.findByEventIdIn(eventIds)
                .forEach(preferences -> eventIdsWithPreferences.add(preferences.getEventId()));

        Set<UUID> eventIdsWithInterpretation = new HashSet<>();
        interpretationRepository.findByEventIdIn(eventIds)
                .forEach(interpretation -> eventIdsWithInterpretation.add(interpretation.getEventId()));

        Map<UUID, LiveOutfitRecommendation> latestGenerationByEventId = new HashMap<>();
        liveRecommendationRepository.findByEventIdInOrderByEventIdAscGenerationDesc(eventIds)
                .forEach(recommendation ->
                        latestGenerationByEventId.putIfAbsent(recommendation.getEventId(), recommendation));

        return events.stream()
                .map(event -> toResponse(
                        event,
                        eventIdsWithPreferences.contains(event.getId()),
                        eventIdsWithInterpretation.contains(event.getId()),
                        latestGenerationByEventId.get(event.getId())))
                .toList();
    }

    private CalendarEventResponse toResponse(
            Event event, boolean hasPreferences, boolean hasInterpretation, LiveOutfitRecommendation latest) {
        LiveRecommendationCompleteness recommendationStatus = latest == null ? null : latest.getCompleteness();
        boolean stale = latest != null && latest.isStale();

        CalendarStylingStatus stylingStatus;
        if (!hasPreferences) {
            stylingStatus = CalendarStylingStatus.EVENT_ONLY;
        } else if (!hasInterpretation) {
            stylingStatus = CalendarStylingStatus.PREFERENCES_SET;
        } else if (latest == null) {
            stylingStatus = CalendarStylingStatus.INTERPRETATION_READY;
        } else if (stale) {
            stylingStatus = CalendarStylingStatus.RECOMMENDATIONS_STALE;
        } else if (recommendationStatus == LiveRecommendationCompleteness.COMPLETE
                || recommendationStatus == LiveRecommendationCompleteness.PARTIAL) {
            stylingStatus = CalendarStylingStatus.RECOMMENDATIONS_READY;
        } else {
            stylingStatus = CalendarStylingStatus.RECOMMENDATIONS_PENDING;
        }

        return new CalendarEventResponse(
                event.getId(),
                event.getTitle(),
                event.getStartTime(),
                event.getEndTime(),
                event.getStartTime().getOffset().getId(),
                isAllDay(event),
                event.getLocation(),
                event.getSetting(),
                event.getDressCode(),
                stylingStatus,
                recommendationStatus,
                stale,
                true);
    }

    private boolean isAllDay(Event event) {
        OffsetDateTime start = event.getStartTime();
        OffsetDateTime end = event.getEndTime();
        return start.toLocalTime().equals(LocalTime.MIDNIGHT)
                && end.toLocalTime().equals(LocalTime.MIDNIGHT)
                && !start.isEqual(end);
    }
}
