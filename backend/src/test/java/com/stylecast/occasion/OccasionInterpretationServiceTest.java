package com.stylecast.occasion;

import com.stylecast.event.Event;
import com.stylecast.event.EventNotFoundException;
import com.stylecast.event.EventRepository;
import com.stylecast.event.EventSetting;
import com.stylecast.event.styling.EventStylePreferencesRepository;
import com.stylecast.occasion.dto.OccasionInterpretationResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OccasionInterpretationServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventStylePreferencesRepository preferencesRepository;

    @Mock
    private OccasionInterpretationRepository interpretationRepository;

    @Mock
    private OpenAiOccasionClassifier openAiClassifier;

    @Mock
    private RuleBasedOccasionClassifier ruleBasedClassifier;

    private OccasionInterpretationService service;

    private Event sampleEvent(UUID eventId) {
        return new Event(
                eventId,
                "Sarah & Tom's Wedding",
                "Outdoor garden ceremony",
                "123 Main St, Springfield",
                OffsetDateTime.now().plusDays(30),
                OffsetDateTime.now().plusDays(30).plusHours(4),
                EventSetting.OUTDOOR,
                null,
                Instant.now());
    }

    private OccasionClassificationResult aiResult() {
        return new OccasionClassificationResult(
                OccasionType.WEDDING,
                InterpretedDressCode.GARDEN_COCKTAIL,
                8,
                List.of(com.stylecast.catalog.ProductCategory.SUIT),
                List.of(com.stylecast.catalog.ProductCategory.ACCESSORY),
                List.of("navy"),
                List.of(),
                List.of(SpecialRequirement.OUTDOOR_SUITABLE),
                List.of("Outdoor garden wedding implies cocktail-adjacent formality."),
                new BigDecimal("0.88"),
                InterpretationSource.AI,
                "gpt-4.1");
    }

    private OccasionClassificationResult fallbackResult() {
        return new OccasionClassificationResult(
                OccasionType.WEDDING,
                InterpretedDressCode.GARDEN_COCKTAIL,
                8,
                List.of(com.stylecast.catalog.ProductCategory.SUIT),
                List.of(),
                List.of(),
                List.of(),
                List.of(SpecialRequirement.OUTDOOR_SUITABLE),
                List.of("Classified using keyword matching against event text; no live weather data was used."),
                new BigDecimal("0.45"),
                InterpretationSource.RULE_BASED_FALLBACK,
                null);
    }

    private void initService() {
        service = new OccasionInterpretationService(
                eventRepository, preferencesRepository, interpretationRepository, openAiClassifier, ruleBasedClassifier);
    }

    @Test
    void getInterpretation_whenNoneExists_classifiesAndPersists() {
        initService();
        UUID eventId = UUID.randomUUID();
        Event event = sampleEvent(eventId);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(preferencesRepository.findByEventId(eventId)).thenReturn(Optional.empty());
        when(interpretationRepository.findByEventId(eventId)).thenReturn(Optional.empty());
        when(openAiClassifier.classify(any())).thenReturn(aiResult());
        when(interpretationRepository.save(any(OccasionInterpretation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OccasionInterpretationResponse response = service.getInterpretation(eventId);

        verify(openAiClassifier).classify(any());
        verify(ruleBasedClassifier, never()).classify(any());
        ArgumentCaptor<OccasionInterpretation> captor = ArgumentCaptor.forClass(OccasionInterpretation.class);
        verify(interpretationRepository).save(captor.capture());
        assertThat(captor.getValue().getEventId()).isEqualTo(eventId);
        assertThat(response.occasion()).isEqualTo(OccasionType.WEDDING);
        assertThat(response.source()).isEqualTo(InterpretationSource.AI);
    }

    @Test
    void getInterpretation_whenAlreadyExists_returnsSavedRowWithoutCallingAnyClassifier() {
        initService();
        UUID eventId = UUID.randomUUID();
        Event event = sampleEvent(eventId);
        OccasionInterpretation existing = new OccasionInterpretation(UUID.randomUUID(), eventId, Instant.now());
        existing.apply(aiResult(), Instant.now());
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(interpretationRepository.findByEventId(eventId)).thenReturn(Optional.of(existing));

        OccasionInterpretationResponse response = service.getInterpretation(eventId);

        verifyNoInteractions(openAiClassifier);
        verifyNoInteractions(ruleBasedClassifier);
        verify(interpretationRepository, never()).save(any());
        assertThat(response.occasion()).isEqualTo(OccasionType.WEDDING);
    }

    @Test
    void getInterpretation_whenEventDoesNotExist_throwsEventNotFound() {
        initService();
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getInterpretation(eventId)).isInstanceOf(EventNotFoundException.class);
        verifyNoInteractions(interpretationRepository);
    }

    @Test
    void regenerateInterpretation_alwaysInvokesProviderAndUpdatesExistingRowWithoutDuplicating() {
        initService();
        UUID eventId = UUID.randomUUID();
        Event event = sampleEvent(eventId);
        UUID existingId = UUID.randomUUID();
        OccasionInterpretation existing = new OccasionInterpretation(existingId, eventId, Instant.now());
        existing.apply(fallbackResult(), Instant.now().minusSeconds(3600));

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(interpretationRepository.findByEventId(eventId)).thenReturn(Optional.of(existing));
        when(openAiClassifier.classify(any())).thenReturn(aiResult());
        when(interpretationRepository.save(any(OccasionInterpretation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Instant beforeGeneratedAt = existing.getGeneratedAt();
        OccasionInterpretationResponse response = service.regenerateInterpretation(eventId);

        verify(openAiClassifier).classify(any());
        ArgumentCaptor<OccasionInterpretation> captor = ArgumentCaptor.forClass(OccasionInterpretation.class);
        verify(interpretationRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(existingId);
        assertThat(response.id()).isEqualTo(existingId);
        assertThat(response.source()).isEqualTo(InterpretationSource.AI);
        assertThat(response.generatedAt()).isAfter(beforeGeneratedAt);
    }

    @Test
    void classify_whenOpenAiThrows_fallsBackToRuleBasedAndMarksSourceAccordingly() {
        initService();
        UUID eventId = UUID.randomUUID();
        Event event = sampleEvent(eventId);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(preferencesRepository.findByEventId(eventId)).thenReturn(Optional.empty());
        when(interpretationRepository.findByEventId(eventId)).thenReturn(Optional.empty());
        when(openAiClassifier.classify(any()))
                .thenThrow(new OccasionClassificationException("simulated provider timeout"));
        when(ruleBasedClassifier.classify(any())).thenReturn(fallbackResult());
        when(interpretationRepository.save(any(OccasionInterpretation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OccasionInterpretationResponse response = service.getInterpretation(eventId);

        verify(ruleBasedClassifier).classify(any());
        assertThat(response.source()).isEqualTo(InterpretationSource.RULE_BASED_FALLBACK);
    }

    @Test
    void classify_whenOpenAiThrowsDueToMissingApiKey_fallsBackToRuleBased() {
        initService();
        UUID eventId = UUID.randomUUID();
        Event event = sampleEvent(eventId);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(preferencesRepository.findByEventId(eventId)).thenReturn(Optional.empty());
        when(interpretationRepository.findByEventId(eventId)).thenReturn(Optional.empty());
        when(openAiClassifier.classify(any()))
                .thenThrow(new OccasionClassificationException("OPENAI_API_KEY is not set"));
        when(ruleBasedClassifier.classify(any())).thenReturn(fallbackResult());
        when(interpretationRepository.save(any(OccasionInterpretation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OccasionInterpretationResponse response = service.getInterpretation(eventId);

        assertThat(response.source()).isEqualTo(InterpretationSource.RULE_BASED_FALLBACK);
    }
}
