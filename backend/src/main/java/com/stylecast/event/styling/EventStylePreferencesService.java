package com.stylecast.event.styling;

import com.stylecast.auth.CurrentUserProvider;
import com.stylecast.event.EventNotFoundException;
import com.stylecast.event.EventRepository;
import com.stylecast.event.styling.dto.EventStylePreferencesResponse;
import com.stylecast.event.styling.dto.UpsertEventStylePreferencesRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service for event styling preferences. Enforces the
 * one-preferences-record-per-event rule: a {@code PUT} either creates the
 * first record for an event or updates the existing one, never inserting a
 * duplicate.
 */
@Service
public class EventStylePreferencesService {

    private final EventStylePreferencesRepository preferencesRepository;
    private final EventRepository eventRepository;
    private final CurrentUserProvider currentUserProvider;

    public EventStylePreferencesService(
            EventStylePreferencesRepository preferencesRepository,
            EventRepository eventRepository,
            CurrentUserProvider currentUserProvider) {
        this.preferencesRepository = preferencesRepository;
        this.eventRepository = eventRepository;
        this.currentUserProvider = currentUserProvider;
    }

    public EventStylePreferencesResponse getPreferences(UUID eventId) {
        requireEventExists(eventId);

        EventStylePreferences preferences = preferencesRepository.findByEventId(eventId)
                .orElseThrow(() -> new EventStylePreferencesNotFoundException(eventId));
        return EventStylePreferencesResponse.fromEntity(preferences);
    }

    public EventStylePreferencesResponse upsertPreferences(
            UUID eventId, UpsertEventStylePreferencesRequest request) {
        requireEventExists(eventId);

        Instant now = Instant.now();
        Optional<EventStylePreferences> existing = preferencesRepository.findByEventId(eventId);
        EventStylePreferences preferences = existing
                .orElseGet(() -> new EventStylePreferences(UUID.randomUUID(), eventId, now));

        InterpretationRelevantSnapshot before = existing.map(InterpretationRelevantSnapshot::of).orElse(null);

        preferences.apply(
                request.outfitRequest(),
                request.maxBudget(),
                request.clothingSize(),
                request.shoeSize(),
                request.preferredStyle(),
                request.preferredColors(),
                request.colorsToAvoid(),
                request.shoppingDepartment(),
                now);

        EventStylePreferences saved = preferencesRepository.save(preferences);

        boolean interpretationRefreshRecommended =
                before != null && !before.equals(InterpretationRelevantSnapshot.of(saved));

        return EventStylePreferencesResponse.fromEntity(saved, interpretationRefreshRecommended);
    }

    /**
     * The subset of a saved event's preferences that actually feeds {@code
     * OccasionClassificationInput} (see {@code
     * OccasionInterpretationService.buildInput}) - {@code maxBudget}/{@code
     * clothingSize}/{@code shoeSize}/{@code shoppingDepartment} never
     * influence the occasion interpretation, so changing only those fields
     * must never recommend an interpretation refresh.
     */
    private record InterpretationRelevantSnapshot(
            String outfitRequest,
            PreferredStyle preferredStyle,
            List<String> preferredColors,
            List<String> colorsToAvoid) {

        static InterpretationRelevantSnapshot of(EventStylePreferences preferences) {
            return new InterpretationRelevantSnapshot(
                    preferences.getOutfitRequest(),
                    preferences.getPreferredStyle(),
                    preferences.getPreferredColors(),
                    preferences.getColorsToAvoid());
        }
    }

    private void requireEventExists(UUID eventId) {
        if (eventRepository.findByIdAndUserId(eventId, currentUserProvider.requireCurrentUserId()).isEmpty()) {
            throw new EventNotFoundException(eventId);
        }
    }
}
