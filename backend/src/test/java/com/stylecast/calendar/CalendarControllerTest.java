package com.stylecast.calendar;

import com.stylecast.calendar.dto.CalendarEventResponse;
import com.stylecast.catalog.ProductCategory;
import com.stylecast.common.error.ApiError;
import com.stylecast.event.Event;
import com.stylecast.event.EventRepository;
import com.stylecast.event.EventSetting;
import com.stylecast.event.styling.EventStylePreferences;
import com.stylecast.event.styling.EventStylePreferencesRepository;
import com.stylecast.event.styling.PreferredStyle;
import com.stylecast.occasion.InterpretationSource;
import com.stylecast.occasion.InterpretedDressCode;
import com.stylecast.occasion.OccasionClassificationResult;
import com.stylecast.occasion.OccasionInterpretation;
import com.stylecast.occasion.OccasionInterpretationRepository;
import com.stylecast.occasion.OccasionType;
import com.stylecast.occasion.SpecialRequirement;
import com.stylecast.recommendation.LiveOutfitRecommendation;
import com.stylecast.recommendation.LiveOutfitRecommendationRepository;
import com.stylecast.recommendation.LiveRecommendationCompleteness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import com.stylecast.testsupport.NoExternalNetworkGuardConfig;
import com.stylecast.testsupport.TestAuthSupport;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-request tests for {@code GET /api/events/calendar}. Every scenario
 * uses a real Postgres (Testcontainers) instance and the real
 * authentication filter chain - no fake classifier/search provider bean is
 * needed since this endpoint never calls either (see {@link
 * CalendarEventService} javadoc): it only ever reads already-persisted
 * preferences/interpretation/recommendation rows.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
@ActiveProfiles("test")
@Import(NoExternalNetworkGuardConfig.class)
class CalendarControllerTest {

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
    private EventStylePreferencesRepository preferencesRepository;

    @Autowired
    private OccasionInterpretationRepository interpretationRepository;

    @Autowired
    private LiveOutfitRecommendationRepository liveRecommendationRepository;

    private TestAuthSupport.InstalledAuth auth;

    @BeforeEach
    void setUp() {
        liveRecommendationRepository.deleteAll();
        interpretationRepository.deleteAll();
        preferencesRepository.deleteAll();
        eventRepository.deleteAll();
        auth = TestAuthSupport.installAuthenticatedUser(restTemplate, port);
    }

    @AfterEach
    void clearAuthentication() {
        TestAuthSupport.uninstall(restTemplate, auth);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private String calendarUrl(OffsetDateTime start, OffsetDateTime end) {
        return url("/api/events/calendar?start=" + start + "&end=" + end);
    }

    private Event saveEvent(UUID userId, String title, OffsetDateTime start, OffsetDateTime end) {
        return eventRepository.save(new Event(
                UUID.randomUUID(), userId, title, "description", "123 Main St, Springfield",
                start, end, EventSetting.INDOOR, "Casual", Instant.now()));
    }

    private void savePreferences(UUID eventId) {
        EventStylePreferences preferences = new EventStylePreferences(UUID.randomUUID(), eventId, Instant.now());
        preferences.apply("Something stylish", BigDecimal.valueOf(500), "M", "9", PreferredStyle.CLASSIC,
                List.of("navy"), List.of(), Instant.now());
        preferencesRepository.save(preferences);
    }

    private void saveInterpretation(UUID eventId) {
        OccasionInterpretation interpretation = new OccasionInterpretation(UUID.randomUUID(), eventId, Instant.now());
        interpretation.apply(new OccasionClassificationResult(
                OccasionType.DINNER, InterpretedDressCode.FORMAL, 6, List.of(ProductCategory.SUIT), List.of(),
                List.of(), List.of(), List.of(SpecialRequirement.NOT_OVERLY_FORMAL), List.of(), List.of(),
                BigDecimal.valueOf(0.9), InterpretationSource.RULE_BASED_FALLBACK, null), Instant.now());
        interpretationRepository.save(interpretation);
    }

    private void saveLiveRecommendation(UUID eventId, LiveRecommendationCompleteness completeness, boolean stale) {
        LiveOutfitRecommendation recommendation = LiveOutfitRecommendation.active(
                eventId, 1, 0, "Look 1", "explanation", completeness,
                List.of(ProductCategory.SUIT), List.of(), List.of(), List.of(), null, Instant.now());
        if (stale) {
            recommendation.markStale(Instant.now());
        }
        liveRecommendationRepository.save(recommendation);
    }

    @Test
    void unauthenticatedRequest_returns401() {
        TestRestTemplate anonymous = new TestRestTemplate();
        OffsetDateTime start = OffsetDateTime.now();
        OffsetDateTime end = start.plusDays(30);

        ResponseEntity<ApiError> response = anonymous.getForEntity(
                calendarUrl(start, end), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void getCalendarEvents_returnsOnlyTheCallingUsersEvents() {
        OffsetDateTime start = OffsetDateTime.now();
        OffsetDateTime end = start.plusDays(30);
        Event ownEvent = saveEvent(auth.userId(), "My event", start.plusDays(1), start.plusDays(1).plusHours(1));

        TestAuthSupport.AuthenticatedTestUser otherUser = TestAuthSupport.registerAndLogin(restTemplate, port);
        saveEvent(otherUser.userId(), "Someone else's event", start.plusDays(1), start.plusDays(1).plusHours(1));

        ResponseEntity<CalendarEventResponse[]> response = restTemplate.getForEntity(
                calendarUrl(start, end), CalendarEventResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<UUID> ids = List.of(response.getBody()).stream().map(CalendarEventResponse::id).toList();
        assertThat(ids).containsExactly(ownEvent.getId());
    }

    @Test
    void getCalendarEvents_includesEventsThatOverlapTheRangeBoundaries() {
        OffsetDateTime rangeStart = OffsetDateTime.now();
        OffsetDateTime rangeEnd = rangeStart.plusDays(7);

        Event startsBeforeEndsInside = saveEvent(
                auth.userId(), "Starts before", rangeStart.minusHours(2), rangeStart.plusHours(1));
        Event spansWholeRange = saveEvent(
                auth.userId(), "Multi-day", rangeStart.minusDays(1), rangeEnd.plusDays(1));
        Event fullyInside = saveEvent(
                auth.userId(), "Inside", rangeStart.plusDays(2), rangeStart.plusDays(2).plusHours(1));

        ResponseEntity<CalendarEventResponse[]> response = restTemplate.getForEntity(
                calendarUrl(rangeStart, rangeEnd), CalendarEventResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<UUID> ids = List.of(response.getBody()).stream().map(CalendarEventResponse::id).toList();
        // Ordered by startTime ascending: spansWholeRange starts a full day before rangeStart,
        // startsBeforeEndsInside starts only 2 hours before it.
        assertThat(ids).containsExactly(spansWholeRange.getId(), startsBeforeEndsInside.getId(), fullyInside.getId());
    }

    @Test
    void getCalendarEvents_excludesEventsOutsideTheRange() {
        OffsetDateTime rangeStart = OffsetDateTime.now();
        OffsetDateTime rangeEnd = rangeStart.plusDays(7);

        saveEvent(auth.userId(), "Before range", rangeStart.minusDays(5), rangeStart.minusDays(5).plusHours(1));
        saveEvent(auth.userId(), "After range", rangeEnd.plusDays(5), rangeEnd.plusDays(5).plusHours(1));
        Event inRange = saveEvent(auth.userId(), "In range", rangeStart.plusDays(1), rangeStart.plusDays(1).plusHours(1));

        ResponseEntity<CalendarEventResponse[]> response = restTemplate.getForEntity(
                calendarUrl(rangeStart, rangeEnd), CalendarEventResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<UUID> ids = List.of(response.getBody()).stream().map(CalendarEventResponse::id).toList();
        assertThat(ids).containsExactly(inRange.getId());
    }

    @Test
    void getCalendarEvents_withStartAfterEnd_returns400() {
        OffsetDateTime start = OffsetDateTime.now();
        OffsetDateTime end = start.minusDays(1);

        ResponseEntity<ApiError> response = restTemplate.getForEntity(calendarUrl(start, end), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getCalendarEvents_withRangeExceedingMaximum_returns400() {
        OffsetDateTime start = OffsetDateTime.now();
        OffsetDateTime end = start.plus(CalendarEventService.MAX_RANGE).plusDays(1);

        ResponseEntity<ApiError> response = restTemplate.getForEntity(calendarUrl(start, end), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getCalendarEvents_stylingStatus_eventOnlyWhenNoPreferencesSaved() {
        OffsetDateTime start = OffsetDateTime.now();
        Event event = saveEvent(auth.userId(), "No preferences", start.plusDays(1), start.plusDays(1).plusHours(1));

        CalendarEventResponse result = fetchOne(start, start.plusDays(30), event.getId());

        assertThat(result.stylingStatus()).isEqualTo(CalendarStylingStatus.EVENT_ONLY);
        assertThat(result.recommendationStatus()).isNull();
        assertThat(result.stale()).isFalse();
        assertThat(result.canEdit()).isTrue();
    }

    @Test
    void getCalendarEvents_stylingStatus_preferencesSetWhenNoInterpretationYet() {
        OffsetDateTime start = OffsetDateTime.now();
        Event event = saveEvent(auth.userId(), "Preferences only", start.plusDays(1), start.plusDays(1).plusHours(1));
        savePreferences(event.getId());

        CalendarEventResponse result = fetchOne(start, start.plusDays(30), event.getId());

        assertThat(result.stylingStatus()).isEqualTo(CalendarStylingStatus.PREFERENCES_SET);
    }

    @Test
    void getCalendarEvents_stylingStatus_interpretationReadyWhenNoGenerationYet() {
        OffsetDateTime start = OffsetDateTime.now();
        Event event = saveEvent(auth.userId(), "Interpretation only", start.plusDays(1), start.plusDays(1).plusHours(1));
        savePreferences(event.getId());
        saveInterpretation(event.getId());

        CalendarEventResponse result = fetchOne(start, start.plusDays(30), event.getId());

        assertThat(result.stylingStatus()).isEqualTo(CalendarStylingStatus.INTERPRETATION_READY);
    }

    @Test
    void getCalendarEvents_stylingStatus_recommendationsPendingWhenGenerationFoundNothing() {
        OffsetDateTime start = OffsetDateTime.now();
        Event event = saveEvent(auth.userId(), "No results yet", start.plusDays(1), start.plusDays(1).plusHours(1));
        savePreferences(event.getId());
        saveInterpretation(event.getId());
        saveLiveRecommendation(event.getId(), LiveRecommendationCompleteness.NO_RESULTS, false);

        CalendarEventResponse result = fetchOne(start, start.plusDays(30), event.getId());

        assertThat(result.stylingStatus()).isEqualTo(CalendarStylingStatus.RECOMMENDATIONS_PENDING);
        assertThat(result.recommendationStatus()).isEqualTo(LiveRecommendationCompleteness.NO_RESULTS);
    }

    @Test
    void getCalendarEvents_stylingStatus_recommendationsReadyWhenGenerationSucceeded() {
        OffsetDateTime start = OffsetDateTime.now();
        Event event = saveEvent(auth.userId(), "Ready", start.plusDays(1), start.plusDays(1).plusHours(1));
        savePreferences(event.getId());
        saveInterpretation(event.getId());
        saveLiveRecommendation(event.getId(), LiveRecommendationCompleteness.COMPLETE, false);

        CalendarEventResponse result = fetchOne(start, start.plusDays(30), event.getId());

        assertThat(result.stylingStatus()).isEqualTo(CalendarStylingStatus.RECOMMENDATIONS_READY);
        assertThat(result.stale()).isFalse();
    }

    @Test
    void getCalendarEvents_stylingStatus_recommendationsStaleWhenMarkedStale() {
        OffsetDateTime start = OffsetDateTime.now();
        Event event = saveEvent(auth.userId(), "Stale", start.plusDays(1), start.plusDays(1).plusHours(1));
        savePreferences(event.getId());
        saveInterpretation(event.getId());
        saveLiveRecommendation(event.getId(), LiveRecommendationCompleteness.COMPLETE, true);

        CalendarEventResponse result = fetchOne(start, start.plusDays(30), event.getId());

        assertThat(result.stylingStatus()).isEqualTo(CalendarStylingStatus.RECOMMENDATIONS_STALE);
        assertThat(result.stale()).isTrue();
    }

    @Test
    void getCalendarEvents_neverCreatesInterpretationOrRecommendationRows() {
        OffsetDateTime start = OffsetDateTime.now();
        Event event = saveEvent(auth.userId(), "Untouched", start.plusDays(1), start.plusDays(1).plusHours(1));
        savePreferences(event.getId());

        restTemplate.getForEntity(calendarUrl(start, start.plusDays(30)), CalendarEventResponse[].class);

        assertThat(interpretationRepository.findByEventId(event.getId())).isEmpty();
        assertThat(liveRecommendationRepository.findFirstByEventIdOrderByGenerationDesc(event.getId())).isEmpty();
    }

    private CalendarEventResponse fetchOne(OffsetDateTime start, OffsetDateTime end, UUID eventId) {
        ResponseEntity<CalendarEventResponse[]> response = restTemplate.getForEntity(
                calendarUrl(start, end), CalendarEventResponse[].class);
        return List.of(response.getBody()).stream()
                .filter(event -> event.id().equals(eventId))
                .findFirst()
                .orElseThrow();
    }
}
