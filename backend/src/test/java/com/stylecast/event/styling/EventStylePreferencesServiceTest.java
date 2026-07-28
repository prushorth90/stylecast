package com.stylecast.event.styling;

import com.stylecast.event.EventNotFoundException;
import com.stylecast.event.EventRepository;
import com.stylecast.event.styling.dto.EventStylePreferencesResponse;
import com.stylecast.event.styling.dto.UpsertEventStylePreferencesRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventStylePreferencesServiceTest {

    @Mock
    private EventStylePreferencesRepository preferencesRepository;

    @Mock
    private EventRepository eventRepository;

    private EventStylePreferencesService service;

    private UpsertEventStylePreferencesRequest sampleRequest() {
        return new UpsertEventStylePreferencesRequest(
                "  I want a navy suit and tie, but not too formal.  ",
                new BigDecimal("500.00"),
                "  M  ",
                "  10  ",
                PreferredStyle.CLASSIC,
                List.of(" navy ", "cream", ""),
                List.of("bright red"));
    }

    @Test
    void upsertPreferences_whenNoneExist_createsNewRecordTrimmedAndNormalized() {
        service = new EventStylePreferencesService(preferencesRepository, eventRepository);
        UUID eventId = UUID.randomUUID();
        when(eventRepository.existsById(eventId)).thenReturn(true);
        when(preferencesRepository.findByEventId(eventId)).thenReturn(Optional.empty());
        when(preferencesRepository.save(any(EventStylePreferences.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EventStylePreferencesResponse response = service.upsertPreferences(eventId, sampleRequest());

        ArgumentCaptor<EventStylePreferences> captor = ArgumentCaptor.forClass(EventStylePreferences.class);
        verify(preferencesRepository).save(captor.capture());
        EventStylePreferences saved = captor.getValue();

        assertThat(saved.getEventId()).isEqualTo(eventId);
        assertThat(saved.getOutfitRequest()).isEqualTo("I want a navy suit and tie, but not too formal.");
        assertThat(saved.getClothingSize()).isEqualTo("M");
        assertThat(saved.getShoeSize()).isEqualTo("10");
        assertThat(saved.getPreferredColors()).containsExactly("navy", "cream");
        assertThat(saved.getColorsToAvoid()).containsExactly("bright red");
        assertThat(response.eventId()).isEqualTo(eventId);
        assertThat(response.maxBudget()).isEqualByComparingTo("500.00");
    }

    @Test
    void upsertPreferences_whenExisting_updatesSameRecordWithoutCreatingDuplicate() {
        service = new EventStylePreferencesService(preferencesRepository, eventRepository);
        UUID eventId = UUID.randomUUID();
        EventStylePreferences existing = new EventStylePreferences(UUID.randomUUID(), eventId, Instant.now());
        when(eventRepository.existsById(eventId)).thenReturn(true);
        when(preferencesRepository.findByEventId(eventId)).thenReturn(Optional.of(existing));
        when(preferencesRepository.save(any(EventStylePreferences.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EventStylePreferencesResponse response = service.upsertPreferences(eventId, sampleRequest());

        ArgumentCaptor<EventStylePreferences> captor = ArgumentCaptor.forClass(EventStylePreferences.class);
        verify(preferencesRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(existing.getId());
        assertThat(response.id()).isEqualTo(existing.getId());
    }

    @Test
    void upsertPreferences_withUnknownEvent_throwsWithoutSaving() {
        service = new EventStylePreferencesService(preferencesRepository, eventRepository);
        UUID unknownEventId = UUID.randomUUID();
        when(eventRepository.existsById(unknownEventId)).thenReturn(false);

        assertThatThrownBy(() -> service.upsertPreferences(unknownEventId, sampleRequest()))
                .isInstanceOf(EventNotFoundException.class)
                .hasMessageContaining(unknownEventId.toString());

        verify(preferencesRepository, never()).save(any());
    }

    @Test
    void getPreferences_withExisting_returnsResponse() {
        service = new EventStylePreferencesService(preferencesRepository, eventRepository);
        UUID eventId = UUID.randomUUID();
        EventStylePreferences existing = new EventStylePreferences(UUID.randomUUID(), eventId, Instant.now());
        existing.apply(
                "Outfit request", new BigDecimal("100.00"), "M", "9", PreferredStyle.MODERN,
                List.of("black"), List.of(), Instant.now());
        when(eventRepository.existsById(eventId)).thenReturn(true);
        when(preferencesRepository.findByEventId(eventId)).thenReturn(Optional.of(existing));

        EventStylePreferencesResponse response = service.getPreferences(eventId);

        assertThat(response.eventId()).isEqualTo(eventId);
        assertThat(response.preferredStyle()).isEqualTo(PreferredStyle.MODERN);
    }

    @Test
    void getPreferences_whenNoneSaved_throwsEventStylePreferencesNotFoundException() {
        service = new EventStylePreferencesService(preferencesRepository, eventRepository);
        UUID eventId = UUID.randomUUID();
        when(eventRepository.existsById(eventId)).thenReturn(true);
        when(preferencesRepository.findByEventId(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPreferences(eventId))
                .isInstanceOf(EventStylePreferencesNotFoundException.class);
    }

    @Test
    void getPreferences_withUnknownEvent_throwsEventNotFoundException() {
        service = new EventStylePreferencesService(preferencesRepository, eventRepository);
        UUID unknownEventId = UUID.randomUUID();
        when(eventRepository.existsById(unknownEventId)).thenReturn(false);

        assertThatThrownBy(() -> service.getPreferences(unknownEventId))
                .isInstanceOf(EventNotFoundException.class);
    }
}
