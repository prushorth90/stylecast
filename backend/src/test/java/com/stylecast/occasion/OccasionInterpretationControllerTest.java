package com.stylecast.occasion;

import com.stylecast.common.error.ApiError;
import com.stylecast.event.Event;
import com.stylecast.event.EventRepository;
import com.stylecast.event.EventSetting;
import com.stylecast.occasion.dto.OccasionInterpretationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Full-request tests for {@code GET /api/events/{eventId}/interpretation} and
 * {@code POST /api/events/{eventId}/interpretation/regenerate}.
 *
 * <p>The test application configuration never sets {@code OPENAI_API_KEY}, so
 * the real {@link OpenAiOccasionClassifier} bean always fails fast (no
 * network call attempted) and every classification in this test class
 * exercises {@link RuleBasedOccasionClassifier} - these tests never call the
 * live OpenAI API. AI-path response normalization is covered separately by
 * {@code OpenAiOccasionClassifierTest} and {@code OccasionInterpretationServiceTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class OccasionInterpretationControllerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private OccasionInterpretationRepository interpretationRepository;

    private UUID eventId;

    @BeforeEach
    void setUp() {
        interpretationRepository.deleteAll();
        eventRepository.deleteAll();

        Event event = eventRepository.save(new Event(
                UUID.randomUUID(),
                "Sarah & Tom's Wedding",
                "Outdoor garden ceremony and reception",
                "123 Main St, Springfield",
                OffsetDateTime.now().plusDays(30),
                OffsetDateTime.now().plusDays(30).plusHours(4),
                EventSetting.OUTDOOR,
                null,
                Instant.now()));
        eventId = event.getId();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void getInterpretation_withMalformedEventId_returns400() {
        ResponseEntity<ApiError> response =
                restTemplate.getForEntity(url("/api/events/not-a-uuid/interpretation"), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getInterpretation_withUnknownEventId_returns404() {
        ResponseEntity<ApiError> response =
                restTemplate.getForEntity(url("/api/events/" + UUID.randomUUID() + "/interpretation"), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getInterpretation_whenNoneExists_automaticallyClassifiesAndPersistsUsingRuleBasedFallback() {
        ResponseEntity<OccasionInterpretationResponse> response =
                restTemplate.getForEntity(
                        url("/api/events/" + eventId + "/interpretation"), OccasionInterpretationResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        OccasionInterpretationResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.eventId()).isEqualTo(eventId);
        assertThat(body.occasion()).isEqualTo(OccasionType.WEDDING);
        assertThat(body.source()).isEqualTo(InterpretationSource.RULE_BASED_FALLBACK);
        assertThat(interpretationRepository.findByEventId(eventId)).isPresent();
    }

    @Test
    void getInterpretation_calledTwice_returnsSameRowWithoutCreatingDuplicate() {
        OccasionInterpretationResponse first = restTemplate.getForEntity(
                url("/api/events/" + eventId + "/interpretation"), OccasionInterpretationResponse.class).getBody();

        OccasionInterpretationResponse second = restTemplate.getForEntity(
                url("/api/events/" + eventId + "/interpretation"), OccasionInterpretationResponse.class).getBody();

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(second.id()).isEqualTo(first.id());
        // Compare within a strict 1-microsecond tolerance rather than exact equality: the
        // first response may still carry the original Instant from before it was ever
        // persisted, while the second response is read back from PostgreSQL's TIMESTAMPTZ
        // column, which stores (and rounds to, not truncates to) microsecond precision - so
        // the two values can legitimately differ by exactly one microsecond due to rounding.
        assertThat(second.generatedAt()).isCloseTo(first.generatedAt(), within(1, ChronoUnit.MICROS));
        // Exactly one interpretation row exists for the event - the second GET never created a duplicate.
        assertThat(interpretationRepository.findAll()).hasSize(1);
    }

    @Test
    void regenerateInterpretation_updatesSameRowWithNewGeneratedAtWithoutDuplicating() throws InterruptedException {
        OccasionInterpretationResponse first = restTemplate.getForEntity(
                url("/api/events/" + eventId + "/interpretation"), OccasionInterpretationResponse.class).getBody();
        assertThat(first).isNotNull();

        // Ensure a measurable clock difference between the first generation and the regeneration.
        Thread.sleep(5);

        ResponseEntity<OccasionInterpretationResponse> regenerateResponse = restTemplate.postForEntity(
                url("/api/events/" + eventId + "/interpretation/regenerate"), null, OccasionInterpretationResponse.class);

        assertThat(regenerateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        OccasionInterpretationResponse regenerated = regenerateResponse.getBody();
        assertThat(regenerated).isNotNull();
        assertThat(regenerated.id()).isEqualTo(first.id());
        assertThat(regenerated.generatedAt()).isAfter(first.generatedAt());
        assertThat(interpretationRepository.findAll()).hasSize(1);
    }

    @Test
    void regenerateInterpretation_withUnknownEventId_returns404() {
        ResponseEntity<ApiError> response = restTemplate.postForEntity(
                url("/api/events/" + UUID.randomUUID() + "/interpretation/regenerate"), null, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void regenerateInterpretation_withMalformedEventId_returns400() {
        ResponseEntity<ApiError> response = restTemplate.postForEntity(
                url("/api/events/not-a-uuid/interpretation/regenerate"), null, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
