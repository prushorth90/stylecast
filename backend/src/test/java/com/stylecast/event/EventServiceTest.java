package com.stylecast.event;

import com.stylecast.event.dto.CreateEventRequest;
import com.stylecast.event.dto.EventResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.OffsetDateTime;
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
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    private EventService eventService;

    @Test
    void createEvent_withEndAfterStart_savesAndReturnsResponse() {
        eventService = new EventService(eventRepository);
        OffsetDateTime start = OffsetDateTime.now().plusDays(1);
        CreateEventRequest request = new CreateEventRequest(
                "Gallery opening", "Art show", "Downtown gallery",
                start, start.plusHours(2), EventSetting.INDOOR, "Cocktail attire");

        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EventResponse response = eventService.createEvent(request);

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository).save(captor.capture());
        Event savedEvent = captor.getValue();

        assertThat(savedEvent.getTitle()).isEqualTo("Gallery opening");
        assertThat(savedEvent.getSetting()).isEqualTo(EventSetting.INDOOR);
        assertThat(response.title()).isEqualTo("Gallery opening");
        assertThat(response.dressCode()).isEqualTo("Cocktail attire");
    }

    @Test
    void createEvent_withEndNotAfterStart_throwsWithoutSaving() {
        eventService = new EventService(eventRepository);
        OffsetDateTime start = OffsetDateTime.now().plusDays(1);
        CreateEventRequest request = new CreateEventRequest(
                "Gallery opening", null, "Downtown gallery",
                start, start, EventSetting.INDOOR, null);

        assertThatThrownBy(() -> eventService.createEvent(request))
                .isInstanceOf(InvalidEventException.class)
                .hasMessageContaining("endTime must be after startTime");

        verify(eventRepository, never()).save(any());
    }

    @Test
    void getEvent_withUnknownId_throwsEventNotFoundException() {
        eventService = new EventService(eventRepository);
        UUID unknownId = UUID.randomUUID();
        when(eventRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.getEvent(unknownId))
                .isInstanceOf(EventNotFoundException.class)
                .hasMessageContaining(unknownId.toString());
    }

    @Test
    void listUpcomingEvents_delegatesToRepositoryOrderedByStartTime() {
        eventService = new EventService(eventRepository);
        Event event = new Event(
                UUID.randomUUID(), "Concert", null, "Arena",
                OffsetDateTime.now().plusDays(1), OffsetDateTime.now().plusDays(1).plusHours(2),
                EventSetting.OUTDOOR, null, Instant.now());
        when(eventRepository.findByEndTimeAfterOrderByStartTimeAsc(any(OffsetDateTime.class)))
                .thenReturn(List.of(event));

        List<EventResponse> result = eventService.listUpcomingEvents();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("Concert");
    }
}
