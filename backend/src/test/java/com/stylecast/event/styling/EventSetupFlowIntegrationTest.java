package com.stylecast.event.styling;

import com.stylecast.catalog.ProductCategory;
import com.stylecast.common.error.ApiError;
import com.stylecast.event.Event;
import com.stylecast.event.EventRepository;
import com.stylecast.event.EventSetting;
import com.stylecast.occasion.OccasionInterpretationRepository;
import com.stylecast.recommendation.LiveOutfitRecommendation;
import com.stylecast.recommendation.LiveOutfitRecommendationRepository;
import com.stylecast.recommendation.LiveRecommendationCompleteness;
import com.stylecast.testsupport.NoExternalNetworkGuardConfig;
import com.stylecast.testsupport.TestAuthSupport;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end backend tests for the two-step event setup flow (event details
 * -&gt; styling preferences), exercised entirely through the real REST
 * endpoints the frontend's {@code EventSetupModal} calls: {@code POST}/
 * {@code PUT /api/events[/{eventId}]}, {@code PUT .../preferences}, {@code
 * POST .../interpretation/regenerate}, and {@code POST
 * .../recommendations/live/invalidate-stale}.
 *
 * <p>Never exercises the live Nordstrom search pipeline itself (no fake
 * {@code RetailProductSearchProvider} is wired here) - tests that need an
 * existing live recommendation row seed one directly via the repository,
 * since saving preferences must never trigger a live search regardless.
 * {@code POST .../interpretation/regenerate} DOES exercise occasion
 * classification, though - {@link #forceNoRealOpenAiCalls} unconditionally
 * overrides the OpenAI base URL/key at the highest property-source
 * precedence (above OS environment variables) so it always falls back to
 * {@code RuleBasedOccasionClassifier} deterministically, regardless of the
 * environment running these tests; {@link NoExternalNetworkGuardConfig}
 * additionally fails the test immediately if that override is ever lost.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
@ActiveProfiles("test")
@Import(NoExternalNetworkGuardConfig.class)
class EventSetupFlowIntegrationTest {

    @DynamicPropertySource
    static void forceNoRealOpenAiCalls(DynamicPropertyRegistry registry) {
        registry.add("stylecast.occasion-classifier.openai-api-key", () -> "");
        registry.add("stylecast.occasion-classifier.base-url", () -> "http://localhost:1");
    }

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
    private LiveOutfitRecommendationRepository liveOutfitRecommendationRepository;

    private TestAuthSupport.InstalledAuth auth;

    @BeforeEach
    void cleanDatabase() {
        liveOutfitRecommendationRepository.deleteAll();
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

    private HttpEntity<Map<String, Object>> jsonRequest(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private Map<String, Object> eventRequestBody(String title) {
        OffsetDateTime start = OffsetDateTime.now().plusDays(20);
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("description", "Description");
        body.put("location", "123 Main St, Springfield");
        body.put("startTime", start.toString());
        body.put("endTime", start.plusHours(3).toString());
        body.put("setting", "OUTDOOR");
        body.put("dressCode", "Casual");
        return body;
    }

    private Map<String, Object> preferencesRequestBody(String outfitRequest) {
        Map<String, Object> body = new HashMap<>();
        body.put("outfitRequest", outfitRequest);
        body.put("maxBudget", "500.00");
        body.put("clothingSize", "M");
        body.put("shoeSize", "10");
        body.put("preferredStyle", "CASUAL");
        body.put("preferredColors", List.of());
        body.put("colorsToAvoid", List.of());
        body.put("shoppingDepartment", "NO_PREFERENCE");
        return body;
    }

    private UUID createEvent(String title) {
        ResponseEntity<EventResponseBody> response = restTemplate.postForEntity(
                url("/api/events"), jsonRequest(eventRequestBody(title)), EventResponseBody.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().id();
    }

    @Test
    void newEvent_isCreatedOnceAcrossBothModalSteps() {
        UUID eventId = createEvent("Rooftop birthday party");

        ResponseEntity<PreferencesResponseBody> preferencesResponse = restTemplate.exchange(
                url("/api/events/" + eventId + "/preferences"),
                HttpMethod.PUT,
                jsonRequest(preferencesRequestBody("A navy suit and tie.")),
                PreferencesResponseBody.class);

        assertThat(preferencesResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(eventRepository.findAll()).hasSize(1);
        assertThat(preferencesRepository.findAll()).hasSize(1);
        assertThat(preferencesRepository.findByEventId(eventId)).isPresent();
    }

    @Test
    void backAndContinue_doNotCreateDuplicateEvents() {
        UUID eventId = createEvent("Draft title");

        Map<String, Object> editedOnce = eventRequestBody("Edited once");
        restTemplate.exchange(
                url("/api/events/" + eventId), HttpMethod.PUT, jsonRequest(editedOnce), EventResponseBody.class);

        Map<String, Object> editedAgain = eventRequestBody("Edited again");
        ResponseEntity<EventResponseBody> response = restTemplate.exchange(
                url("/api/events/" + eventId), HttpMethod.PUT, jsonRequest(editedAgain), EventResponseBody.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().id()).isEqualTo(eventId);
        assertThat(eventRepository.findAll()).hasSize(1);
        assertThat(eventRepository.findById(eventId).get().getTitle()).isEqualTo("Edited again");
    }

    @Test
    void editingStep1_updatesTheExistingEvent() {
        UUID eventId = createEvent("Original title");

        Map<String, Object> edited = eventRequestBody("Renamed event");
        ResponseEntity<EventResponseBody> response = restTemplate.exchange(
                url("/api/events/" + eventId), HttpMethod.PUT, jsonRequest(edited), EventResponseBody.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().title()).isEqualTo("Renamed event");
        assertThat(eventRepository.findById(eventId).get().getTitle()).isEqualTo("Renamed event");
    }

    @Test
    void savingChangedPreferences_thenRegenerating_updatesTheInterpretation() {
        UUID eventId = createEvent("Soccer match");

        restTemplate.exchange(
                url("/api/events/" + eventId + "/preferences"),
                HttpMethod.PUT,
                jsonRequest(preferencesRequestBody("A plain white shirt.")),
                PreferencesResponseBody.class);

        InterpretationResponseBody firstInterpretation = restTemplate.getForEntity(
                url("/api/events/" + eventId + "/interpretation"), InterpretationResponseBody.class).getBody();
        assertThat(firstInterpretation).isNotNull();

        ResponseEntity<PreferencesResponseBody> changedResponse = restTemplate.exchange(
                url("/api/events/" + eventId + "/preferences"),
                HttpMethod.PUT,
                jsonRequest(preferencesRequestBody("i want soccer jersey with short and soccer boots")),
                PreferencesResponseBody.class);
        assertThat(changedResponse.getBody().interpretationRefreshRecommended()).isTrue();

        InterpretationResponseBody regenerated = restTemplate.postForEntity(
                url("/api/events/" + eventId + "/interpretation/regenerate"), null, InterpretationResponseBody.class)
                .getBody();

        assertThat(regenerated).isNotNull();
        assertThat(regenerated.requestedItems())
                .extracting(RequestedItemResponseBody::originalPhrase)
                .anyMatch(phrase -> phrase.toLowerCase().contains("soccer"));
    }

    @Test
    void unchangedPreferences_doNotRecommendInterpretationRefresh() {
        UUID eventId = createEvent("Business dinner");
        Map<String, Object> body = preferencesRequestBody("A navy suit and tie.");

        restTemplate.exchange(
                url("/api/events/" + eventId + "/preferences"), HttpMethod.PUT, jsonRequest(body), PreferencesResponseBody.class);

        ResponseEntity<PreferencesResponseBody> secondResponse = restTemplate.exchange(
                url("/api/events/" + eventId + "/preferences"), HttpMethod.PUT, jsonRequest(body), PreferencesResponseBody.class);

        assertThat(secondResponse.getBody().interpretationRefreshRecommended()).isFalse();
    }

    @Test
    void invalidateStale_marksTheLatestGenerationRecommendationsStale() {
        UUID eventId = createEvent("Networking mixer");
        LiveOutfitRecommendation seeded = LiveOutfitRecommendation.active(
                eventId, 1, 1, "Live Look 1", "explanation", LiveRecommendationCompleteness.COMPLETE,
                List.of(ProductCategory.SHIRT), List.of(), List.of(), List.of(), null, Instant.now());
        UUID recommendationId = seeded.getId();
        liveOutfitRecommendationRepository.save(seeded);

        ResponseEntity<Void> response = restTemplate.postForEntity(
                url("/api/events/" + eventId + "/recommendations/live/invalidate-stale"), null, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(liveOutfitRecommendationRepository.findById(recommendationId).get().isStale()).isTrue();
    }

    @Test
    void savingPreferences_neverCreatesALiveRecommendationGeneration() {
        UUID eventId = createEvent("Golf outing");

        restTemplate.exchange(
                url("/api/events/" + eventId + "/preferences"),
                HttpMethod.PUT,
                jsonRequest(preferencesRequestBody("A polo shirt and golf shoes.")),
                PreferencesResponseBody.class);
        restTemplate.exchange(
                url("/api/events/" + eventId + "/preferences"),
                HttpMethod.PUT,
                jsonRequest(preferencesRequestBody("A different polo shirt and golf shoes.")),
                PreferencesResponseBody.class);

        assertThat(liveOutfitRecommendationRepository.findAll()).isEmpty();
    }

    @Test
    void failedPreferenceSave_preservesTheCreatedEvent() {
        UUID eventId = createEvent("Conference talk");

        Map<String, Object> invalidBody = preferencesRequestBody("   ");
        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/events/" + eventId + "/preferences"), HttpMethod.PUT, jsonRequest(invalidBody), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<EventResponseBody> eventResponse = restTemplate.getForEntity(
                url("/api/events/" + eventId), EventResponseBody.class);
        assertThat(eventResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(eventRepository.findById(eventId)).isPresent();
    }

    private record EventResponseBody(
            UUID id,
            String title,
            String description,
            String location,
            String startTime,
            String endTime,
            String setting,
            String dressCode,
            String createdAt) {
    }

    private record PreferencesResponseBody(
            UUID id,
            UUID eventId,
            String outfitRequest,
            String maxBudget,
            String clothingSize,
            String shoeSize,
            String preferredStyle,
            List<String> preferredColors,
            List<String> colorsToAvoid,
            String shoppingDepartment,
            String createdAt,
            String updatedAt,
            boolean interpretationRefreshRecommended) {
    }

    private record RequestedItemResponseBody(
            UUID id,
            String originalPhrase,
            String genericCategory,
            List<String> searchTerms,
            boolean required,
            String activityContext,
            int displayOrder) {
    }

    private record InterpretationResponseBody(
            UUID id,
            UUID eventId,
            String occasion,
            String dressCode,
            int formalityLevel,
            List<String> requiredCategories,
            List<String> optionalCategories,
            List<String> preferredColors,
            List<String> colorsToAvoid,
            List<String> specialRequirements,
            List<String> assumptions,
            List<RequestedItemResponseBody> requestedItems,
            String confidence,
            String source,
            String generatedAt,
            String createdAt,
            String updatedAt) {
    }
}
