package com.stylecast.recommendation;

import com.stylecast.event.Event;
import com.stylecast.event.EventNotFoundException;
import com.stylecast.event.EventRepository;
import com.stylecast.event.styling.EventStylePreferences;
import com.stylecast.event.styling.EventStylePreferencesRepository;
import com.stylecast.occasion.OccasionInterpretation;
import com.stylecast.occasion.OccasionInterpretationRepository;
import com.stylecast.weather.EventWeatherSnapshotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RecommendationContextLoader}'s prerequisite checks.
 */
@ExtendWith(MockitoExtension.class)
class RecommendationContextLoaderTest {

    @Mock
    private EventRepository eventRepository;
    @Mock
    private EventStylePreferencesRepository preferencesRepository;
    @Mock
    private OccasionInterpretationRepository interpretationRepository;
    @Mock
    private EventWeatherSnapshotRepository weatherSnapshotRepository;

    private RecommendationContextLoader loader() {
        return new RecommendationContextLoader(eventRepository, preferencesRepository, interpretationRepository, weatherSnapshotRepository);
    }

    @Test
    void requireEvent_withUnknownEventId_throwsEventNotFoundException() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loader().requireEvent(eventId)).isInstanceOf(EventNotFoundException.class);
    }

    @Test
    void load_withUnknownEventId_throwsEventNotFoundException() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loader().load(eventId)).isInstanceOf(EventNotFoundException.class);
    }

    @Test
    void load_withoutSavedPreferences_throwsMissingStylePreferencesException() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(RecommendationFixtures.event()));
        when(preferencesRepository.findByEventId(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loader().load(eventId)).isInstanceOf(MissingStylePreferencesException.class);
    }

    @Test
    void load_withoutOccasionInterpretation_throwsMissingOccasionInterpretationException() {
        UUID eventId = UUID.randomUUID();
        Event event = RecommendationFixtures.event();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        EventStylePreferences preferences = mockPreferences(eventId);
        when(preferencesRepository.findByEventId(eventId)).thenReturn(Optional.of(preferences));
        when(interpretationRepository.findByEventId(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loader().load(eventId)).isInstanceOf(MissingOccasionInterpretationException.class);
    }

    @Test
    void load_withEveryPrerequisitePresent_returnsPopulatedContextEvenWithoutWeather() {
        UUID eventId = UUID.randomUUID();
        Event event = RecommendationFixtures.event();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        EventStylePreferences preferences = mockPreferences(eventId);
        when(preferencesRepository.findByEventId(eventId)).thenReturn(Optional.of(preferences));
        OccasionInterpretation interpretation = mockInterpretation(eventId);
        when(interpretationRepository.findByEventId(eventId)).thenReturn(Optional.of(interpretation));
        when(weatherSnapshotRepository.findByEventId(eventId)).thenReturn(Optional.empty());

        RecommendationContext context = loader().load(eventId);

        assertThat(context.event()).isEqualTo(event);
        assertThat(context.preferences()).isEqualTo(preferences);
        assertThat(context.interpretation()).isEqualTo(interpretation);
        assertThat(context.weather()).isEmpty();
        assertThat(context.weatherSignal().available()).isFalse();
    }

    private EventStylePreferences mockPreferences(UUID eventId) {
        return RecommendationFixtures.preferences(
                eventId, java.math.BigDecimal.valueOf(500), "M", "9",
                com.stylecast.event.styling.PreferredStyle.CLASSIC, java.util.List.of(), java.util.List.of());
    }

    private OccasionInterpretation mockInterpretation(UUID eventId) {
        return RecommendationFixtures.interpretation(
                eventId, com.stylecast.occasion.OccasionType.WEDDING, 8,
                java.util.List.of(com.stylecast.catalog.ProductCategory.SUIT), java.util.List.of(), java.util.List.of());
    }
}
