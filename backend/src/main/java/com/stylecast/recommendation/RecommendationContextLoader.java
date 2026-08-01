package com.stylecast.recommendation;

import com.stylecast.auth.CurrentUserProvider;
import com.stylecast.event.Event;
import com.stylecast.event.EventNotFoundException;
import com.stylecast.event.EventRepository;
import com.stylecast.event.styling.EventStylePreferencesRepository;
import com.stylecast.occasion.OccasionInterpretationRepository;
import com.stylecast.weather.EventWeatherSnapshotRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Loads and validates every input the recommendation engine needs for one
 * event: the event itself, its saved styling preferences, its occasion
 * interpretation, and its latest weather snapshot (optional).
 *
 * <p>Kept as a separate component (rather than inline in {@link
 * RecommendationService}) so the "what must exist before we can generate"
 * prerequisite checks are in one place and independently testable.
 */
@Component
public class RecommendationContextLoader {

    private final EventRepository eventRepository;
    private final EventStylePreferencesRepository preferencesRepository;
    private final OccasionInterpretationRepository interpretationRepository;
    private final EventWeatherSnapshotRepository weatherSnapshotRepository;
    private final CurrentUserProvider currentUserProvider;

    public RecommendationContextLoader(
            EventRepository eventRepository,
            EventStylePreferencesRepository preferencesRepository,
            OccasionInterpretationRepository interpretationRepository,
            EventWeatherSnapshotRepository weatherSnapshotRepository,
            CurrentUserProvider currentUserProvider) {
        this.eventRepository = eventRepository;
        this.preferencesRepository = preferencesRepository;
        this.interpretationRepository = interpretationRepository;
        this.weatherSnapshotRepository = weatherSnapshotRepository;
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * Confirms the event exists and returns it. Used by both the {@code GET}
     * (read-only) and {@code generate} entry points, since both need to
     * return 404 for an unknown event id.
     */
    public Event requireEvent(UUID eventId) {
        return eventRepository.findByIdAndUserId(eventId, currentUserProvider.requireCurrentUserId())
                .orElseThrow(() -> new EventNotFoundException(eventId));
    }

    /**
     * Loads the full context required to generate recommendations. Throws
     * {@link EventNotFoundException} (404), {@link
     * MissingStylePreferencesException} (409), or {@link
     * MissingOccasionInterpretationException} (409) when a prerequisite is
     * missing - weather is the only optional input.
     */
    public RecommendationContext load(UUID eventId) {
        Event event = requireEvent(eventId);

        var preferences = preferencesRepository.findByEventId(eventId)
                .orElseThrow(() -> new MissingStylePreferencesException(eventId));

        var interpretation = interpretationRepository.findByEventId(eventId)
                .orElseThrow(() -> new MissingOccasionInterpretationException(eventId));

        var weather = weatherSnapshotRepository.findByEventId(eventId);

        return new RecommendationContext(event, preferences, interpretation, weather);
    }
}
