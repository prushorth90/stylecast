package com.stylecast.occasion;

import com.stylecast.event.EventSetting;
import com.stylecast.event.styling.PreferredStyle;

import java.util.List;

/**
 * Input facts an {@link OccasionClassifier} uses to interpret an event's
 * occasion and dress code.
 *
 * <p>Deliberately excludes weather, budget, and sizing - occasion
 * classification must not depend on (or invent) live weather, and budget/
 * sizing are enforced later by the deterministic recommendation engine, not
 * the occasion classifier.
 *
 * @param eventTitle       required
 * @param eventDescription optional, may be {@code null}
 * @param eventSetting     required (indoor/outdoor)
 * @param manualDressCode  optional, manually entered dress code text, may be {@code null}
 * @param outfitRequest    optional, from saved {@code EventStylePreferences}, may be {@code null}
 * @param preferredStyle   optional, from saved {@code EventStylePreferences}, may be {@code null}
 * @param preferredColors  optional, from saved {@code EventStylePreferences}, never {@code null} (empty if none)
 * @param colorsToAvoid    optional, from saved {@code EventStylePreferences}, never {@code null} (empty if none)
 */
public record OccasionClassificationInput(
        String eventTitle,
        String eventDescription,
        EventSetting eventSetting,
        String manualDressCode,
        String outfitRequest,
        PreferredStyle preferredStyle,
        List<String> preferredColors,
        List<String> colorsToAvoid
) {
    public OccasionClassificationInput {
        preferredColors = preferredColors == null ? List.of() : List.copyOf(preferredColors);
        colorsToAvoid = colorsToAvoid == null ? List.of() : List.copyOf(colorsToAvoid);
    }
}
