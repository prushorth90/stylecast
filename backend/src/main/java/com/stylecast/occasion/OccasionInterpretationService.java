package com.stylecast.occasion;

import com.stylecast.event.Event;
import com.stylecast.event.EventNotFoundException;
import com.stylecast.event.EventRepository;
import com.stylecast.event.styling.EventStylePreferences;
import com.stylecast.event.styling.EventStylePreferencesRepository;
import com.stylecast.occasion.dto.OccasionInterpretationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service for event occasion interpretation. Enforces the
 * one-interpretation-per-event rule: an automatic {@code GET} either
 * generates the first interpretation for an event or returns the existing
 * one as-is (never re-classifying), while {@code regenerate} always
 * re-classifies and overwrites the existing row.
 *
 * <p>Classification always tries {@link OpenAiOccasionClassifier} first and
 * falls back to {@link RuleBasedOccasionClassifier} whenever the AI
 * classifier throws {@link OccasionClassificationException} - including
 * when no API key is configured, on a provider timeout/failure, or when the
 * AI output fails validation. Invalid AI output is therefore never
 * persisted.
 */
@Service
public class OccasionInterpretationService {

    private static final Logger log = LoggerFactory.getLogger(OccasionInterpretationService.class);

    private final EventRepository eventRepository;
    private final EventStylePreferencesRepository preferencesRepository;
    private final OccasionInterpretationRepository interpretationRepository;
    private final OpenAiOccasionClassifier openAiClassifier;
    private final RuleBasedOccasionClassifier ruleBasedClassifier;

    public OccasionInterpretationService(
            EventRepository eventRepository,
            EventStylePreferencesRepository preferencesRepository,
            OccasionInterpretationRepository interpretationRepository,
            OpenAiOccasionClassifier openAiClassifier,
            RuleBasedOccasionClassifier ruleBasedClassifier) {
        this.eventRepository = eventRepository;
        this.preferencesRepository = preferencesRepository;
        this.interpretationRepository = interpretationRepository;
        this.openAiClassifier = openAiClassifier;
        this.ruleBasedClassifier = ruleBasedClassifier;
    }

    /**
     * Returns the event's occasion interpretation, generating and persisting
     * it automatically on first call. Never requires a prior call to
     * {@link #regenerateInterpretation}.
     */
    @Transactional
    public OccasionInterpretationResponse getInterpretation(UUID eventId) {
        Event event = requireEvent(eventId);
        Optional<OccasionInterpretation> existing = interpretationRepository.findByEventId(eventId);
        if (existing.isPresent()) {
            return OccasionInterpretationResponse.fromEntity(existing.get());
        }
        return generateAndSave(event, existing);
    }

    /**
     * Forces a fresh classification regardless of whether an interpretation
     * already exists, and overwrites the existing row (same {@code id}, new
     * {@code generatedAt}) rather than inserting a duplicate.
     */
    @Transactional
    public OccasionInterpretationResponse regenerateInterpretation(UUID eventId) {
        Event event = requireEvent(eventId);
        Optional<OccasionInterpretation> existing = interpretationRepository.findByEventId(eventId);
        return generateAndSave(event, existing);
    }

    private OccasionInterpretationResponse generateAndSave(Event event, Optional<OccasionInterpretation> existing) {
        Instant now = Instant.now();
        OccasionClassificationInput input = buildInput(event);
        OccasionClassificationResult result = classify(input);

        OccasionInterpretation interpretation = existing
                .orElseGet(() -> new OccasionInterpretation(UUID.randomUUID(), event.getId(), now));
        interpretation.apply(result, now);

        OccasionInterpretation saved = interpretationRepository.save(interpretation);
        return OccasionInterpretationResponse.fromEntity(saved);
    }

    private OccasionClassificationInput buildInput(Event event) {
        EventStylePreferences preferences = preferencesRepository.findByEventId(event.getId()).orElse(null);

        return new OccasionClassificationInput(
                event.getTitle(),
                event.getDescription(),
                event.getSetting(),
                event.getDressCode(),
                preferences != null ? preferences.getOutfitRequest() : null,
                preferences != null ? preferences.getPreferredStyle() : null,
                preferences != null ? preferences.getPreferredColors() : List.of(),
                preferences != null ? preferences.getColorsToAvoid() : List.of());
    }

    private OccasionClassificationResult classify(OccasionClassificationInput input) {
        try {
            return openAiClassifier.classify(input);
        } catch (OccasionClassificationException ex) {
            log.warn("Occasion classification via OpenAI unavailable, using rule-based fallback: {}", ex.getMessage());
            return ruleBasedClassifier.classify(input);
        }
    }

    private Event requireEvent(UUID eventId) {
        return eventRepository.findById(eventId).orElseThrow(() -> new EventNotFoundException(eventId));
    }
}
