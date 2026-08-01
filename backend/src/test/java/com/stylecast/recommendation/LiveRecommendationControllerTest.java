package com.stylecast.recommendation;

import com.stylecast.catalog.ProductCategory;
import com.stylecast.common.error.ApiError;
import com.stylecast.event.Event;
import com.stylecast.event.EventRepository;
import com.stylecast.event.EventSetting;
import com.stylecast.event.styling.EventStylePreferences;
import com.stylecast.event.styling.EventStylePreferencesRepository;
import com.stylecast.event.styling.PreferredStyle;
import com.stylecast.event.styling.ShoppingDepartment;
import com.stylecast.occasion.GenericItemCategory;
import com.stylecast.occasion.OccasionInterpretation;
import com.stylecast.occasion.OccasionInterpretationRepository;
import com.stylecast.occasion.OccasionType;
import com.stylecast.occasion.RequestedItem;
import com.stylecast.recommendation.dto.LiveGenerationJobResponse;
import com.stylecast.recommendation.dto.LiveOutfitItemResponse;
import com.stylecast.recommendation.dto.LiveRecommendationsResponse;
import com.stylecast.retail.CandidateAudience;
import com.stylecast.retail.RetailProductCandidate;
import com.stylecast.retail.RetailProductSearchProvider;
import com.stylecast.retail.RetailProductSearchRequest;
import com.stylecast.retail.RetailProductSearchResult;
import com.stylecast.retail.RetailProductSource;
import com.stylecast.retail.Retailer;
import com.stylecast.retail.TargetAudience;
import com.stylecast.retail.ProductSearchProviderException;
import com.stylecast.weather.EventWeatherSnapshotRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import com.stylecast.testsupport.NoExternalNetworkGuardConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-request tests for {@code POST /api/events/{eventId}/recommendations/live/generate},
 * {@code POST .../recommendations/live/retry-missing}, and {@code GET
 * .../recommendations/live}.
 *
 * <p>The real {@link RetailProductSearchProvider} (which would call the
 * live OpenAI API) is replaced with {@link FakeRetailProductSearchProvider},
 * a per-category-configurable fake, so these tests never make a real
 * network call to OpenAI or nordstrom.com.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
@ActiveProfiles("test")
@Import(NoExternalNetworkGuardConfig.class)
class LiveRecommendationControllerTest {

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
    private EventWeatherSnapshotRepository weatherSnapshotRepository;

    @Autowired
    private LiveOutfitRecommendationRepository liveRecommendationRepository;

    @Autowired
    private FakeRetailProductSearchProvider fakeProvider;

    @BeforeEach
    void cleanDatabase() {
        liveRecommendationRepository.deleteAll();
        weatherSnapshotRepository.deleteAll();
        interpretationRepository.deleteAll();
        preferencesRepository.deleteAll();
        eventRepository.deleteAll();
        fakeProvider.reset();
    }

    @AfterEach
    void resetFake() {
        fakeProvider.reset();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private UUID createEvent(String title) {
        Event event = eventRepository.save(new Event(
                UUID.randomUUID(), title, "description", "123 Main St, Springfield",
                OffsetDateTime.now().plusDays(20), OffsetDateTime.now().plusDays(20).plusHours(4),
                EventSetting.INDOOR, null, Instant.now()));
        return event.getId();
    }

    private void savePreferences(UUID eventId, BigDecimal maxBudget) {
        EventStylePreferences preferences = new EventStylePreferences(UUID.randomUUID(), eventId, Instant.now());
        preferences.apply("Something stylish", maxBudget, "M", "9", PreferredStyle.CLASSIC, List.of("navy"), List.of(), Instant.now());
        preferencesRepository.save(preferences);
    }

    private void saveInterpretation(UUID eventId, OccasionType occasion, int formalityLevel, List<ProductCategory> requiredCategories) {
        OccasionInterpretation interpretation = RecommendationFixtures.interpretation(
                eventId, occasion, formalityLevel, requiredCategories, List.of(), List.of());
        interpretationRepository.save(interpretation);
    }

    private RetailProductCandidate candidate(String url) {
        return new RetailProductCandidate(
                RetailProductSource.AI_WEB_SEARCH, Retailer.NORDSTROM, "Navy Wedding Suit", null, null, null, null,
                null, url, null, null, null, List.of(), null, false, false, false, CandidateAudience.UNKNOWN,
                Instant.now(), "fake");
    }

    private RetailProductCandidate candidateWithImage(String url, String imageUrl) {
        return new RetailProductCandidate(
                RetailProductSource.AI_WEB_SEARCH, Retailer.NORDSTROM, "Navy Wedding Suit", null, null, null, null,
                null, url, imageUrl, null, null, List.of(), null, false, false, false, CandidateAudience.UNKNOWN,
                Instant.now(), "fake");
    }

    /**
     * Drives the full asynchronous generation flow exactly as the frontend
     * would (start job -&gt; poll status -&gt; fetch result) and returns the
     * final {@code GET .../recommendations/live} response, so every
     * existing caller of this helper keeps working unchanged even though
     * {@code POST .../generate} itself now only returns a fast HTTP 202 job
     * acknowledgement rather than the recommendations directly.
     */
    private ResponseEntity<LiveRecommendationsResponse> generate(UUID eventId) {
        ResponseEntity<LiveGenerationJobResponse> started = startGenerateJob(eventId);
        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        awaitJobTerminal(eventId);
        return getRecommendations(eventId);
    }

    private ResponseEntity<LiveGenerationJobResponse> startGenerateJob(UUID eventId) {
        return restTemplate.postForEntity(
                url("/api/events/" + eventId + "/recommendations/live/generate"), null, LiveGenerationJobResponse.class);
    }

    private LiveGenerationJobResponse getJobStatus(UUID eventId) {
        return restTemplate.getForEntity(
                url("/api/events/" + eventId + "/recommendations/live/status"), LiveGenerationJobResponse.class).getBody();
    }

    private LiveGenerationJobResponse awaitJobTerminal(UUID eventId) {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            LiveGenerationJobResponse status = getJobStatus(eventId);
            if (status != null && isTerminal(status.status())) {
                return status;
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while polling job status", e);
            }
        }
        throw new AssertionError("Live recommendation generation job for event " + eventId + " did not reach a terminal state in time");
    }

    private boolean isTerminal(LiveGenerationJobStatus status) {
        return status == LiveGenerationJobStatus.COMPLETED
                || status == LiveGenerationJobStatus.PARTIAL
                || status == LiveGenerationJobStatus.FAILED;
    }

    private ResponseEntity<LiveRecommendationsResponse> retryMissing(UUID eventId) {
        return restTemplate.postForEntity(
                url("/api/events/" + eventId + "/recommendations/live/retry-missing"), null, LiveRecommendationsResponse.class);
    }

    private ResponseEntity<LiveRecommendationsResponse> getRecommendations(UUID eventId) {
        return restTemplate.getForEntity(
                url("/api/events/" + eventId + "/recommendations/live"), LiveRecommendationsResponse.class);
    }

    // --- Validation and prerequisite errors ---------------------------------------

    @Test
    void generate_withMalformedEventId_returns400() {
        ResponseEntity<ApiError> response = restTemplate.postForEntity(
                url("/api/events/not-a-uuid/recommendations/live/generate"), null, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void generate_withUnknownEventId_returns404() {
        ResponseEntity<ApiError> response = restTemplate.postForEntity(
                url("/api/events/" + UUID.randomUUID() + "/recommendations/live/generate"), null, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void generate_withoutSavedPreferences_returns409() {
        UUID eventId = createEvent("Networking Mixer");

        ResponseEntity<ApiError> response = restTemplate.postForEntity(
                url("/api/events/" + eventId + "/recommendations/live/generate"), null, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void generate_withoutOccasionInterpretation_returns409() {
        UUID eventId = createEvent("Networking Mixer");
        savePreferences(eventId, BigDecimal.valueOf(1500));

        ResponseEntity<ApiError> response = restTemplate.postForEntity(
                url("/api/events/" + eventId + "/recommendations/live/generate"), null, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // --- Asynchronous job behavior ---------------------------------------------------

    @Test
    void status_withoutAnyGenerationEverStarted_returnsNotStarted() {
        UUID eventId = createEvent("Networking Mixer");
        savePreferences(eventId, BigDecimal.valueOf(1500));
        saveInterpretation(eventId, OccasionType.NETWORKING, 5, List.of(ProductCategory.SUIT));

        LiveGenerationJobResponse status = getJobStatus(eventId);

        assertThat(status).isNotNull();
        assertThat(status.status()).isEqualTo(LiveGenerationJobStatus.NOT_STARTED);
        assertThat(status.jobId()).isNull();
    }

    @Test
    void status_withUnknownEventId_returns404() {
        ResponseEntity<ApiError> response = restTemplate.getForEntity(
                url("/api/events/" + UUID.randomUUID() + "/recommendations/live/status"), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void generate_returns202ImmediatelyWithJobFields_neverBlockingOnTheLiveSearch() {
        UUID eventId = createEvent("Sarah & Tom's Wedding");
        savePreferences(eventId, BigDecimal.valueOf(2000));
        saveInterpretation(eventId, OccasionType.WEDDING, 9, List.of(ProductCategory.SUIT));
        fakeProvider.blockSearches();

        try {
            ResponseEntity<LiveGenerationJobResponse> response = startGenerateJob(eventId);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
            LiveGenerationJobResponse job = response.getBody();
            assertThat(job).isNotNull();
            assertThat(job.eventId()).isEqualTo(eventId);
            assertThat(job.jobId()).isNotNull();
            assertThat(job.startedAt()).isNotNull();
            assertThat(job.status()).isIn(LiveGenerationJobStatus.QUEUED, LiveGenerationJobStatus.PROCESSING);
        } finally {
            fakeProvider.releaseSearches();
        }
    }

    @Test
    void generate_calledTwiceWhileFirstJobStillProcessing_returnsTheSameActiveJobInsteadOfStartingASecondOne() {
        UUID eventId = createEvent("Sarah & Tom's Wedding");
        savePreferences(eventId, BigDecimal.valueOf(2000));
        saveInterpretation(eventId, OccasionType.WEDDING, 9, List.of(ProductCategory.SUIT));
        fakeProvider.blockSearches();

        try {
            LiveGenerationJobResponse firstJob = startGenerateJob(eventId).getBody();
            LiveGenerationJobResponse secondJob = startGenerateJob(eventId).getBody();

            assertThat(firstJob).isNotNull();
            assertThat(secondJob).isNotNull();
            assertThat(secondJob.jobId()).isEqualTo(firstJob.jobId());
        } finally {
            fakeProvider.releaseSearches();
        }

        awaitJobTerminal(eventId);
    }

    @Test
    void generate_whenLiveSearchThrowsAnUnexpectedException_reportsAFailedJobWithoutLeakingExceptionDetails() {
        UUID eventId = createEvent("Sarah & Tom's Wedding");
        savePreferences(eventId, BigDecimal.valueOf(2000));
        saveInterpretation(eventId, OccasionType.WEDDING, 9, List.of(ProductCategory.SUIT));
        fakeProvider.setFailure(new IllegalStateException("some internal detail that must never reach a client"));

        startGenerateJob(eventId);
        LiveGenerationJobResponse finalStatus = awaitJobTerminal(eventId);

        assertThat(finalStatus.status()).isEqualTo(LiveGenerationJobStatus.FAILED);
        assertThat(finalStatus.completedAt()).isNotNull();
        assertThat(finalStatus.message()).isNotNull();
        assertThat(finalStatus.message()).doesNotContain("IllegalStateException", "some internal detail");
    }

    // --- Successful (complete) live generation --------------------------------------

    @Test
    void generate_withCandidatesForEveryRequiredCategory_returnsCompleteLiveOutfits() {
        UUID eventId = createEvent("Sarah & Tom's Wedding");
        savePreferences(eventId, BigDecimal.valueOf(2000));
        saveInterpretation(eventId, OccasionType.WEDDING, 9, List.of(ProductCategory.SUIT));

        fakeProvider.setResult(ProductCategory.SUIT, new RetailProductSearchResult(
                List.of(candidate("https://www.nordstrom.com/s/navy-suit/1111111"))));
        fakeProvider.setResult(ProductCategory.SHOES, new RetailProductSearchResult(
                List.of(candidate("https://www.nordstrom.com/s/oxford-shoes/2222222"))));

        ResponseEntity<LiveRecommendationsResponse> response = generate(eventId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        LiveRecommendationsResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(LiveRecommendationCompleteness.COMPLETE);
        assertThat(body.foundCategories()).containsExactlyInAnyOrder(ProductCategory.SUIT, ProductCategory.SHOES);
        assertThat(body.missingCategories()).isEmpty();
        assertThat(body.message()).isNull();
        assertThat(body.recommendations()).hasSize(1);
        assertThat(body.recommendations().get(0).items()).hasSize(2);
        assertThat(body.recommendations().get(0).items()).allSatisfy(item -> {
            assertThat(item.priceVerified()).isFalse();
            assertThat(item.sizeVerified()).isFalse();
            assertThat(item.productUrl()).startsWith("https://www.nordstrom.com/");
        });

        // GET returns the same generation without re-invoking the provider.
        ResponseEntity<LiveRecommendationsResponse> getResponse = getRecommendations(eventId);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).isNotNull();
        assertThat(getResponse.getBody().generation()).isEqualTo(body.generation());
        assertThat(getResponse.getBody().stale()).isFalse();
    }

    /**
     * {@code imageUrl} stays a nullable field on the entity/DTO purely for
     * backward/database compatibility (see {@code
     * OpenAiProductDetailEnricher}'s class docs - live Nordstrom candidates
     * never set it any more) - this proves that IF a candidate happens to
     * carry a non-null value (e.g. from another source), it still survives
     * persistence and every DTO mapping unchanged.
     */
    @Test
    void generate_withCandidateImageUrl_survivesPersistenceAndApiMapping() {
        UUID eventId = createEvent("Sarah & Tom's Wedding");
        savePreferences(eventId, BigDecimal.valueOf(2000));
        saveInterpretation(eventId, OccasionType.WEDDING, 9, List.of(ProductCategory.SUIT));

        fakeProvider.setResult(ProductCategory.SUIT, new RetailProductSearchResult(
                List.of(candidateWithImage("https://www.nordstrom.com/s/navy-suit/1111111",
                        "https://images.nordstrom.com/navy-suit.jpg"))));
        fakeProvider.setResult(ProductCategory.SHOES, new RetailProductSearchResult(
                List.of(candidate("https://www.nordstrom.com/s/oxford-shoes/2222222"))));

        LiveRecommendationsResponse generated = generate(eventId).getBody();
        assertThat(generated).isNotNull();
        assertThat(generated.recommendations()).hasSize(1);
        assertThat(generated.recommendations().get(0).items())
                .filteredOn(item -> item.productUrl().equals("https://www.nordstrom.com/s/navy-suit/1111111"))
                .singleElement()
                .satisfies(item -> assertThat(item.imageUrl()).isEqualTo("https://images.nordstrom.com/navy-suit.jpg"));

        // Persisted + re-read via GET (a fresh mapping pass, not the in-memory generate() response) - still present.
        LiveRecommendationsResponse fetched = getRecommendations(eventId).getBody();
        assertThat(fetched).isNotNull();
        assertThat(fetched.recommendations().get(0).items())
                .filteredOn(item -> item.productUrl().equals("https://www.nordstrom.com/s/navy-suit/1111111"))
                .singleElement()
                .satisfies(item -> assertThat(item.imageUrl()).isEqualTo("https://images.nordstrom.com/navy-suit.jpg"));
    }

    /**
     * Live Nordstrom images are no longer fetched/enriched at all (no
     * authorized product feed available yet) - a fake candidate that never
     * sets {@code imageUrl} (the realistic case for every new live
     * generation now) must still persist and map through the DTO cleanly
     * as {@code null}, alongside every other reliable field.
     */
    @Test
    void generate_withoutImageUrl_persistsAndMapsTitleAndProductUrlWithNullImageUrl() {
        UUID eventId = createEvent("Job Interview");
        savePreferences(eventId, BigDecimal.valueOf(600));
        saveInterpretation(eventId, OccasionType.INTERVIEW, 6, List.of(ProductCategory.SUIT));

        fakeProvider.setResult(ProductCategory.SUIT, new RetailProductSearchResult(
                List.of(namedCandidate("https://www.nordstrom.com/s/navy-suit/1111111", "Navy Suit"))));
        fakeProvider.setResult(ProductCategory.SHOES, new RetailProductSearchResult(
                List.of(candidate("https://www.nordstrom.com/s/oxford-shoes/2222222"))));

        LiveRecommendationsResponse generated = generate(eventId).getBody();
        assertThat(generated).isNotNull();
        assertThat(generated.recommendations().get(0).items())
                .filteredOn(item -> item.productUrl().equals("https://www.nordstrom.com/s/navy-suit/1111111"))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.title()).isEqualTo("Navy Suit");
                    assertThat(item.imageUrl()).isNull();
                });

        LiveRecommendationsResponse fetched = getRecommendations(eventId).getBody();
        assertThat(fetched).isNotNull();
        assertThat(fetched.recommendations().get(0).items())
                .filteredOn(item -> item.productUrl().equals("https://www.nordstrom.com/s/navy-suit/1111111"))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.title()).isEqualTo("Navy Suit");
                    assertThat(item.imageUrl()).isNull();
                });
    }

    @Test
    void generate_calledTwice_replacesOldImagelessGenerationWithNewOneContainingImages() {
        UUID eventId = createEvent("Sarah & Tom's Wedding");
        savePreferences(eventId, BigDecimal.valueOf(2000));
        saveInterpretation(eventId, OccasionType.WEDDING, 9, List.of(ProductCategory.SUIT));

        // First generation: no image available yet.
        fakeProvider.setResult(ProductCategory.SUIT, new RetailProductSearchResult(
                List.of(candidate("https://www.nordstrom.com/s/navy-suit/1111111"))));
        fakeProvider.setResult(ProductCategory.SHOES, new RetailProductSearchResult(
                List.of(candidate("https://www.nordstrom.com/s/oxford-shoes/2222222"))));

        LiveRecommendationsResponse first = generate(eventId).getBody();
        assertThat(first).isNotNull();
        assertThat(first.recommendations().get(0).items()).allSatisfy(item -> assertThat(item.imageUrl()).isNull());

        // Regenerate: the same product now has a confirmed image.
        fakeProvider.setResult(ProductCategory.SUIT, new RetailProductSearchResult(
                List.of(candidateWithImage("https://www.nordstrom.com/s/navy-suit/1111111",
                        "https://images.nordstrom.com/navy-suit.jpg"))));

        LiveRecommendationsResponse second = generate(eventId).getBody();
        assertThat(second).isNotNull();
        assertThat(second.generation()).isGreaterThan(first.generation());
        assertThat(second.recommendations().get(0).items())
                .filteredOn(item -> item.productUrl().equals("https://www.nordstrom.com/s/navy-suit/1111111"))
                .singleElement()
                .satisfies(item -> assertThat(item.imageUrl()).isEqualTo("https://images.nordstrom.com/navy-suit.jpg"));

        // GET reflects only the latest generation - the old image-less generation is superseded, not shown.
        LiveRecommendationsResponse fetched = getRecommendations(eventId).getBody();
        assertThat(fetched).isNotNull();
        assertThat(fetched.generation()).isEqualTo(second.generation());
        assertThat(fetched.recommendations()).hasSize(1);
        assertThat(fetched.recommendations().get(0).items())
                .filteredOn(item -> item.productUrl().equals("https://www.nordstrom.com/s/navy-suit/1111111"))
                .singleElement()
                .satisfies(item -> assertThat(item.imageUrl()).isEqualTo("https://images.nordstrom.com/navy-suit.jpg"));
    }

    @Test
    void getRecommendations_afterInvalidateStale_reportsStaleTrueWithoutChangingGenerationOrItems() {
        UUID eventId = createEvent("Sarah & Tom's Wedding");
        savePreferences(eventId, BigDecimal.valueOf(2000));
        saveInterpretation(eventId, OccasionType.WEDDING, 9, List.of(ProductCategory.SUIT));

        fakeProvider.setResult(ProductCategory.SUIT, new RetailProductSearchResult(
                List.of(candidate("https://www.nordstrom.com/s/navy-suit/1111111"))));
        fakeProvider.setResult(ProductCategory.SHOES, new RetailProductSearchResult(
                List.of(candidate("https://www.nordstrom.com/s/oxford-shoes/2222222"))));

        LiveRecommendationsResponse generated = generate(eventId).getBody();
        assertThat(generated).isNotNull();
        assertThat(generated.stale()).isFalse();

        ResponseEntity<Void> invalidateResponse = restTemplate.postForEntity(
                url("/api/events/" + eventId + "/recommendations/live/invalidate-stale"), null, Void.class);
        assertThat(invalidateResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<LiveRecommendationsResponse> getResponse = getRecommendations(eventId);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        LiveRecommendationsResponse body = getResponse.getBody();
        assertThat(body).isNotNull();
        assertThat(body.stale()).isTrue();
        // Marking stale never regenerates or drops the existing items - it's a presentation flag only.
        assertThat(body.generation()).isEqualTo(generated.generation());
        assertThat(body.recommendations()).hasSize(1);
        assertThat(body.recommendations().get(0).items()).hasSize(2);
    }

    // --- Partial results: one category missing, others preserved --------------------

    @Test
    void generate_withOneRequiredCategoryEmpty_returnsPartialResultWithValidCandidatesForFoundCategories() {
        UUID eventId = createEvent("Sarah & Tom's Wedding");
        savePreferences(eventId, BigDecimal.valueOf(2000));
        saveInterpretation(eventId, OccasionType.WEDDING, 9, List.of(ProductCategory.SUIT));

        fakeProvider.setResult(ProductCategory.SUIT, new RetailProductSearchResult(
                List.of(candidate("https://www.nordstrom.com/s/navy-suit/1111111"))));
        fakeProvider.setResult(ProductCategory.SHOES, new RetailProductSearchResult(List.of()));

        ResponseEntity<LiveRecommendationsResponse> response = generate(eventId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        LiveRecommendationsResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(LiveRecommendationCompleteness.PARTIAL);
        assertThat(body.foundCategories()).containsExactly(ProductCategory.SUIT);
        assertThat(body.missingCategories()).containsExactly(ProductCategory.SHOES);
        assertThat(body.message()).contains("Suit").contains("Shoes");
        // Valid candidates for the found category are still returned - never discarded.
        assertThat(body.recommendations()).hasSize(1);
        assertThat(body.recommendations().get(0).items()).hasSize(1);
        assertThat(body.recommendations().get(0).items().get(0).category()).isEqualTo(ProductCategory.SUIT);
        assertThat(body.recommendations().get(0).items().get(0).productUrl())
                .isEqualTo("https://www.nordstrom.com/s/navy-suit/1111111");
    }

    @Test
    void generate_whenOneCategoryProviderFailsButOthersSucceed_returnsPartialNotProviderUnavailable() {
        UUID eventId = createEvent("Sarah & Tom's Wedding");
        savePreferences(eventId, BigDecimal.valueOf(2000));
        saveInterpretation(eventId, OccasionType.WEDDING, 9, List.of(ProductCategory.SUIT));

        fakeProvider.setResult(ProductCategory.SUIT, new RetailProductSearchResult(
                List.of(candidate("https://www.nordstrom.com/s/navy-suit/1111111"))));
        fakeProvider.setFailure(ProductCategory.SHOES, new ProductSearchProviderException("boom"));

        ResponseEntity<LiveRecommendationsResponse> response = generate(eventId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        LiveRecommendationsResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(LiveRecommendationCompleteness.PARTIAL);
        assertThat(body.foundCategories()).containsExactly(ProductCategory.SUIT);
        assertThat(body.missingCategories()).containsExactly(ProductCategory.SHOES);
        assertThat(body.recommendations()).hasSize(1);
    }

    @Test
    void generate_withEveryRequiredCategoryEmpty_returnsNoResultsNotAnError() {
        UUID eventId = createEvent("Sarah & Tom's Wedding");
        savePreferences(eventId, BigDecimal.valueOf(2000));
        saveInterpretation(eventId, OccasionType.WEDDING, 9, List.of(ProductCategory.SUIT));

        fakeProvider.setResult(ProductCategory.SUIT, new RetailProductSearchResult(List.of()));
        fakeProvider.setResult(ProductCategory.SHOES, new RetailProductSearchResult(List.of()));

        ResponseEntity<LiveRecommendationsResponse> response = generate(eventId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        LiveRecommendationsResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(LiveRecommendationCompleteness.NO_RESULTS);
        assertThat(body.message()).contains("Suit").contains("Shoes");
        assertThat(body.recommendations()).isEmpty();
    }

    // --- Provider-unavailable: every attempted category search fails -----------------

    @Test
    void generate_whenEveryCategoryProviderFails_returnsProviderUnavailableAndPersistsAPlaceholder() {
        UUID eventId = createEvent("Sarah & Tom's Wedding");
        savePreferences(eventId, BigDecimal.valueOf(2000));
        saveInterpretation(eventId, OccasionType.WEDDING, 9, List.of(ProductCategory.SUIT));

        fakeProvider.setFailure(ProductCategory.SUIT, new ProductSearchProviderException("boom"));
        fakeProvider.setFailure(ProductCategory.SHOES, new ProductSearchProviderException("boom"));

        ResponseEntity<LiveRecommendationsResponse> response = generate(eventId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        LiveRecommendationsResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(LiveRecommendationCompleteness.PROVIDER_UNAVAILABLE);
        assertThat(body.recommendations()).isEmpty();

        // Unlike a genuine "nothing found" outcome, this is persisted so retry-missing
        // and GET can both reflect it.
        ResponseEntity<LiveRecommendationsResponse> getResponse = getRecommendations(eventId);
        assertThat(getResponse.getBody()).isNotNull();
        assertThat(getResponse.getBody().generation()).isEqualTo(body.generation());
        assertThat(getResponse.getBody().status()).isEqualTo(LiveRecommendationCompleteness.PROVIDER_UNAVAILABLE);
    }

    // --- Retry Missing Items: only re-searches missing categories --------------------

    @Test
    void retryMissing_afterPartialResult_onlySearchesTheMissingCategoryAndKeepsTheFoundOne() {
        UUID eventId = createEvent("Sarah & Tom's Wedding");
        savePreferences(eventId, BigDecimal.valueOf(2000));
        saveInterpretation(eventId, OccasionType.WEDDING, 9, List.of(ProductCategory.SUIT));

        fakeProvider.setResult(ProductCategory.SUIT, new RetailProductSearchResult(
                List.of(candidate("https://www.nordstrom.com/s/navy-suit/1111111"))));
        fakeProvider.setResult(ProductCategory.SHOES, new RetailProductSearchResult(List.of()));
        LiveRecommendationsResponse firstGeneration = generate(eventId).getBody();
        assertThat(firstGeneration).isNotNull();
        assertThat(firstGeneration.status()).isEqualTo(LiveRecommendationCompleteness.PARTIAL);
        fakeProvider.resetCallLog();

        fakeProvider.setResult(ProductCategory.SHOES, new RetailProductSearchResult(
                List.of(candidate("https://www.nordstrom.com/s/oxford-shoes/2222222"))));

        ResponseEntity<LiveRecommendationsResponse> retryResponse = retryMissing(eventId);

        assertThat(retryResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        LiveRecommendationsResponse body = retryResponse.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(LiveRecommendationCompleteness.COMPLETE);
        assertThat(body.generation()).isEqualTo(firstGeneration.generation() + 1);
        assertThat(body.recommendations()).hasSize(1);
        assertThat(body.recommendations().get(0).items()).hasSize(2);
        // Only the previously-missing category was actually searched again.
        assertThat(fakeProvider.callLog()).containsExactly(ProductCategory.SHOES);
    }

    @Test
    void retryMissing_withNothingMissing_isANoOpAndMakesNoSearchCalls() {
        UUID eventId = createEvent("Sarah & Tom's Wedding");
        savePreferences(eventId, BigDecimal.valueOf(2000));
        saveInterpretation(eventId, OccasionType.WEDDING, 9, List.of(ProductCategory.SUIT));

        fakeProvider.setResult(ProductCategory.SUIT, new RetailProductSearchResult(
                List.of(candidate("https://www.nordstrom.com/s/navy-suit/1111111"))));
        fakeProvider.setResult(ProductCategory.SHOES, new RetailProductSearchResult(
                List.of(candidate("https://www.nordstrom.com/s/oxford-shoes/2222222"))));
        LiveRecommendationsResponse firstGeneration = generate(eventId).getBody();
        assertThat(firstGeneration).isNotNull();
        assertThat(firstGeneration.status()).isEqualTo(LiveRecommendationCompleteness.COMPLETE);
        fakeProvider.resetCallLog();

        ResponseEntity<LiveRecommendationsResponse> retryResponse = retryMissing(eventId);

        assertThat(retryResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(retryResponse.getBody()).isNotNull();
        assertThat(retryResponse.getBody().generation()).isEqualTo(firstGeneration.generation());
        assertThat(fakeProvider.callLog()).isEmpty();
    }

    @Test
    void retryMissing_withNoPreviousGeneration_behavesLikeAFreshGenerate() {
        UUID eventId = createEvent("Sarah & Tom's Wedding");
        savePreferences(eventId, BigDecimal.valueOf(2000));
        saveInterpretation(eventId, OccasionType.WEDDING, 9, List.of(ProductCategory.SUIT));

        fakeProvider.setResult(ProductCategory.SUIT, new RetailProductSearchResult(
                List.of(candidate("https://www.nordstrom.com/s/navy-suit/1111111"))));
        fakeProvider.setResult(ProductCategory.SHOES, new RetailProductSearchResult(
                List.of(candidate("https://www.nordstrom.com/s/oxford-shoes/2222222"))));

        ResponseEntity<LiveRecommendationsResponse> retryResponse = retryMissing(eventId);

        assertThat(retryResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(retryResponse.getBody()).isNotNull();
        assertThat(retryResponse.getBody().status()).isEqualTo(LiveRecommendationCompleteness.COMPLETE);
        assertThat(retryResponse.getBody().generation()).isEqualTo(1);
    }

    // --- Explicit requested items (Task 8.5): take priority over category templates ---

    private void saveInterpretationWithRequestedItems(
            UUID eventId, OccasionType occasion, int formalityLevel, List<RequestedItem> requestedItems) {
        OccasionInterpretation interpretation = RecommendationFixtures.interpretation(
                eventId, occasion, formalityLevel, List.of(), List.of(), List.of(), requestedItems);
        interpretationRepository.save(interpretation);
    }

    private RequestedItem requestedItem(String phrase, GenericItemCategory category, String activityContext, int order) {
        return new RequestedItem(UUID.randomUUID(), phrase, category, List.of(phrase), true, activityContext, order);
    }

    private RetailProductCandidate namedCandidate(String url, String title) {
        return new RetailProductCandidate(
                RetailProductSource.AI_WEB_SEARCH, Retailer.NORDSTROM, title, null, null, null, null,
                null, url, null, null, null, List.of(), null, false, false, false, CandidateAudience.UNKNOWN,
                Instant.now(), "fake");
    }

    @Test
    void generate_withExplicitRequestedItems_prioritizesThemOverCategoryTemplatesAndNeverSubstitutesMissingOnes() {
        UUID eventId = createEvent("Sunday Soccer Match");
        savePreferences(eventId, BigDecimal.valueOf(300));
        List<RequestedItem> requestedItems = List.of(
                requestedItem("USA soccer jersey", GenericItemCategory.TOP, "soccer", 0),
                requestedItem("soccer shorts", GenericItemCategory.BOTTOM, "soccer", 1),
                requestedItem("football boots", GenericItemCategory.FOOTWEAR, "soccer", 2));
        saveInterpretationWithRequestedItems(eventId, OccasionType.CASUAL_OUTING, 2, requestedItems);

        fakeProvider.setResultForPhrase("USA soccer jersey", new RetailProductSearchResult(
                List.of(namedCandidate("https://www.nordstrom.com/s/usa-soccer-jersey/1111111", "USA Soccer Jersey"))));
        fakeProvider.setResultForPhrase("soccer shorts", new RetailProductSearchResult(
                List.of(namedCandidate("https://www.nordstrom.com/s/soccer-shorts/2222222", "Soccer Shorts"))));
        fakeProvider.setResultForPhrase("football boots", new RetailProductSearchResult(List.of()));

        ResponseEntity<LiveRecommendationsResponse> response = generate(eventId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        LiveRecommendationsResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(LiveRecommendationCompleteness.PARTIAL);

        // Only the explicit-item pipeline ran - never fell back to a category template search.
        assertThat(fakeProvider.requestLog()).allSatisfy(r -> assertThat(r.category()).isNull());
        assertThat(body.foundCategories()).isEmpty();
        assertThat(body.missingCategories()).isEmpty();

        assertThat(body.foundRequestedItems()).extracting(RequestedItemSummary::originalPhrase)
                .containsExactlyInAnyOrder("USA soccer jersey", "soccer shorts");
        assertThat(body.missingRequestedItems()).extracting(RequestedItemSummary::originalPhrase)
                .containsExactly("football boots");
        assertThat(body.message()).contains("football boots");

        // The missing item must remain missing - never substituted with an unrelated product.
        assertThat(body.recommendations()).hasSize(1);
        assertThat(body.recommendations().get(0).items()).hasSize(2);
        assertThat(body.recommendations().get(0).items())
                .extracting(item -> item.requestedItemPhrase())
                .containsExactlyInAnyOrder("USA soccer jersey", "soccer shorts");
        assertThat(body.recommendations().get(0).items())
                .noneMatch(item -> item.title() != null && item.title().toLowerCase().contains("boot"));
    }

    /**
     * Regression test for the confirmed production bug: a merged multi-item
     * phrase ("shirt trousers shoes") must never collapse into a single
     * FOOTWEAR search - each garment is searched independently, and the
     * overall result is only {@code COMPLETE} once every one of them is
     * found (never presented as complete while any is still missing).
     */
    @Test
    void generate_withShirtTrousersShoesRequestedItems_searchesEachIndependentlyAndNeverCollapsesToFootwear() {
        UUID eventId = createEvent("Job Interview");
        savePreferences(eventId, BigDecimal.valueOf(600));
        List<RequestedItem> requestedItems = List.of(
                requestedItem("shirt", GenericItemCategory.TOP, null, 0),
                requestedItem("trousers", GenericItemCategory.BOTTOM, null, 1),
                requestedItem("shoes", GenericItemCategory.FOOTWEAR, null, 2));
        saveInterpretationWithRequestedItems(eventId, OccasionType.INTERVIEW, 6, requestedItems);

        fakeProvider.setResultForPhrase("shirt", new RetailProductSearchResult(
                List.of(namedCandidate("https://www.nordstrom.com/s/dress-shirt/1111111", "White Dress Shirt"))));
        fakeProvider.setResultForPhrase("trousers", new RetailProductSearchResult(
                List.of(namedCandidate("https://www.nordstrom.com/s/trousers/2222222", "Navy Trousers"))));
        fakeProvider.setResultForPhrase("shoes", new RetailProductSearchResult(
                List.of(namedCandidate("https://www.nordstrom.com/s/oxford-shoes/3333333", "Oxford Shoes"))));

        ResponseEntity<LiveRecommendationsResponse> response = generate(eventId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        LiveRecommendationsResponse body = response.getBody();
        assertThat(body).isNotNull();

        // Each garment was searched as its own independent item request - never merged into one.
        assertThat(fakeProvider.phraseCallLog()).containsExactlyInAnyOrder("shirt", "trousers", "shoes");
        assertThat(body.foundRequestedItems()).extracting(RequestedItemSummary::originalPhrase)
                .containsExactlyInAnyOrder("shirt", "trousers", "shoes");
        assertThat(body.missingRequestedItems()).isEmpty();
        // Only COMPLETE once every requested item is found.
        assertThat(body.status()).isEqualTo(LiveRecommendationCompleteness.COMPLETE);

        assertThat(body.recommendations()).hasSize(1);
        List<LiveOutfitItemResponse> items = body.recommendations().get(0).items();
        assertThat(items).hasSize(3);
        assertThat(items).extracting(LiveOutfitItemResponse::requestedItemGenericCategory)
                .containsExactlyInAnyOrder(GenericItemCategory.TOP, GenericItemCategory.BOTTOM, GenericItemCategory.FOOTWEAR);
        // Never collapsed into all-FOOTWEAR (the exact production bug this guards against).
        assertThat(items).extracting(LiveOutfitItemResponse::requestedItemGenericCategory)
                .doesNotContainSequence(GenericItemCategory.FOOTWEAR, GenericItemCategory.FOOTWEAR, GenericItemCategory.FOOTWEAR);
    }

    /**
     * When one of the independently-searched garments is missing, the
     * result must be reported as {@code PARTIAL} (never {@code COMPLETE}) -
     * a partial explicit-item result is never silently presented as a full
     * outfit.
     */
    @Test
    void generate_withShirtTrousersShoesRequestedItemsAndOneMissing_reportsPartialNotComplete() {
        UUID eventId = createEvent("Job Interview");
        savePreferences(eventId, BigDecimal.valueOf(600));
        List<RequestedItem> requestedItems = List.of(
                requestedItem("shirt", GenericItemCategory.TOP, null, 0),
                requestedItem("trousers", GenericItemCategory.BOTTOM, null, 1),
                requestedItem("shoes", GenericItemCategory.FOOTWEAR, null, 2));
        saveInterpretationWithRequestedItems(eventId, OccasionType.INTERVIEW, 6, requestedItems);

        fakeProvider.setResultForPhrase("shirt", new RetailProductSearchResult(
                List.of(namedCandidate("https://www.nordstrom.com/s/dress-shirt/1111111", "White Dress Shirt"))));
        fakeProvider.setResultForPhrase("trousers", new RetailProductSearchResult(
                List.of(namedCandidate("https://www.nordstrom.com/s/trousers/2222222", "Navy Trousers"))));
        fakeProvider.setResultForPhrase("shoes", new RetailProductSearchResult(List.of()));

        ResponseEntity<LiveRecommendationsResponse> response = generate(eventId);

        LiveRecommendationsResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(LiveRecommendationCompleteness.PARTIAL);
        assertThat(body.foundRequestedItems()).extracting(RequestedItemSummary::originalPhrase)
                .containsExactlyInAnyOrder("shirt", "trousers");
        assertThat(body.missingRequestedItems()).extracting(RequestedItemSummary::originalPhrase)
                .containsExactly("shoes");
    }

    @Test
    void generate_withOldInterpretationHavingNoRequestedItems_usesCategoryTemplatePipelineAndLeavesNewFieldsEmpty() {
        UUID eventId = createEvent("Sarah & Tom's Wedding");
        savePreferences(eventId, BigDecimal.valueOf(2000));
        saveInterpretation(eventId, OccasionType.WEDDING, 9, List.of(ProductCategory.SUIT));

        fakeProvider.setResult(ProductCategory.SUIT, new RetailProductSearchResult(
                List.of(candidate("https://www.nordstrom.com/s/navy-suit/1111111"))));
        fakeProvider.setResult(ProductCategory.SHOES, new RetailProductSearchResult(
                List.of(candidate("https://www.nordstrom.com/s/oxford-shoes/2222222"))));

        ResponseEntity<LiveRecommendationsResponse> response = generate(eventId);

        LiveRecommendationsResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(LiveRecommendationCompleteness.COMPLETE);
        assertThat(body.foundRequestedItems()).isEmpty();
        assertThat(body.missingRequestedItems()).isEmpty();
        assertThat(fakeProvider.requestLog()).allSatisfy(r -> assertThat(r.category()).isNotNull());
    }

    @Test
    void retryMissing_withExplicitRequestedItems_onlySearchesThePreviouslyMissingItem() {
        UUID eventId = createEvent("Sunday Soccer Match");
        savePreferences(eventId, BigDecimal.valueOf(300));
        List<RequestedItem> requestedItems = List.of(
                requestedItem("USA soccer jersey", GenericItemCategory.TOP, "soccer", 0),
                requestedItem("football boots", GenericItemCategory.FOOTWEAR, "soccer", 1));
        saveInterpretationWithRequestedItems(eventId, OccasionType.CASUAL_OUTING, 2, requestedItems);

        fakeProvider.setResultForPhrase("USA soccer jersey", new RetailProductSearchResult(
                List.of(namedCandidate("https://www.nordstrom.com/s/usa-soccer-jersey/1111111", "USA Soccer Jersey"))));
        fakeProvider.setResultForPhrase("football boots", new RetailProductSearchResult(List.of()));
        LiveRecommendationsResponse first = generate(eventId).getBody();
        assertThat(first).isNotNull();
        assertThat(first.status()).isEqualTo(LiveRecommendationCompleteness.PARTIAL);
        fakeProvider.resetCallLog();

        fakeProvider.setResultForPhrase("football boots", new RetailProductSearchResult(
                List.of(namedCandidate("https://www.nordstrom.com/s/football-boots/3333333", "Football Boots"))));

        ResponseEntity<LiveRecommendationsResponse> retryResponse = retryMissing(eventId);

        assertThat(retryResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        LiveRecommendationsResponse body = retryResponse.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(LiveRecommendationCompleteness.COMPLETE);
        assertThat(body.recommendations()).hasSize(1);
        assertThat(body.recommendations().get(0).items()).hasSize(2);
        // Only the previously-missing item was actually searched again.
        assertThat(fakeProvider.phraseCallLog()).containsExactly("football boots");
    }

    @Test
    void generate_withExplicitRequestedItems_stillEnforcesDepartmentPreference() {
        UUID eventId = createEvent("Sunday Soccer Match");
        EventStylePreferences preferences = new EventStylePreferences(UUID.randomUUID(), eventId, Instant.now());
        preferences.apply("Something stylish", BigDecimal.valueOf(300), "M", "9", PreferredStyle.CLASSIC,
                List.of("navy"), List.of(), ShoppingDepartment.MEN, Instant.now());
        preferencesRepository.save(preferences);
        List<RequestedItem> requestedItems = List.of(
                requestedItem("USA soccer jersey", GenericItemCategory.TOP, "soccer", 0));
        saveInterpretationWithRequestedItems(eventId, OccasionType.CASUAL_OUTING, 2, requestedItems);

        fakeProvider.setResultForPhrase("USA soccer jersey", new RetailProductSearchResult(
                List.of(namedCandidate("https://www.nordstrom.com/s/usa-soccer-jersey/1111111", "USA Soccer Jersey"))));

        generate(eventId);

        assertThat(fakeProvider.requestLog()).allSatisfy(r -> assertThat(r.targetAudience()).isEqualTo(TargetAudience.MEN));
    }

    @TestConfiguration
    static class FakeProviderConfig {
        @Bean
        @Primary
        FakeRetailProductSearchProvider fakeRetailProductSearchProvider() {
            return new FakeRetailProductSearchProvider();
        }
    }
    static class FakeRetailProductSearchProvider implements RetailProductSearchProvider {
        private final Map<ProductCategory, RetailProductSearchResult> resultsByCategory = new EnumMap<>(ProductCategory.class);
        private final Map<ProductCategory, RuntimeException> failuresByCategory = new EnumMap<>(ProductCategory.class);
        private final Map<String, RetailProductSearchResult> resultsByPhrase = new java.util.LinkedHashMap<>();
        private final Map<String, RuntimeException> failuresByPhrase = new java.util.LinkedHashMap<>();
        private final List<ProductCategory> callLog = new ArrayList<>();
        private final List<String> phraseCallLog = new ArrayList<>();
        private final List<RetailProductSearchRequest> requestLog = new ArrayList<>();
        private final AtomicReference<RuntimeException> nextFailure = new AtomicReference<>();
        private volatile java.util.concurrent.CountDownLatch blockLatch;

        @Override
        public RetailProductSearchResult search(RetailProductSearchRequest request) {
            requestLog.add(request);
            java.util.concurrent.CountDownLatch latch = blockLatch;
            if (latch != null) {
                try {
                    latch.await(10, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while blocked", e);
                }
            }
            RuntimeException globalFailure = nextFailure.get();
            if (globalFailure != null) {
                throw globalFailure;
            }
            if (request.category() != null) {
                callLog.add(request.category());
                RuntimeException categoryFailure = failuresByCategory.get(request.category());
                if (categoryFailure != null) {
                    throw categoryFailure;
                }
                return resultsByCategory.getOrDefault(request.category(), new RetailProductSearchResult(List.of()));
            }
            // Item-based request (Task 8.5): RequestedItemSearchRequestFactory always adds
            // the item's own originalPhrase as the first keyword, so match on that instead.
            String phraseKey = request.keywords().isEmpty() ? "" : request.keywords().get(0).toLowerCase(java.util.Locale.ROOT);
            phraseCallLog.add(phraseKey);
            RuntimeException phraseFailure = failuresByPhrase.get(phraseKey);
            if (phraseFailure != null) {
                throw phraseFailure;
            }
            return resultsByPhrase.getOrDefault(phraseKey, new RetailProductSearchResult(List.of()));
        }

        void setResult(ProductCategory category, RetailProductSearchResult result) {
            resultsByCategory.put(category, result);
        }

        void setFailure(ProductCategory category, RuntimeException failure) {
            failuresByCategory.put(category, failure);
        }

        void setResultForPhrase(String phrase, RetailProductSearchResult result) {
            resultsByPhrase.put(phrase.toLowerCase(java.util.Locale.ROOT), result);
        }

        void setFailureForPhrase(String phrase, RuntimeException failure) {
            failuresByPhrase.put(phrase.toLowerCase(java.util.Locale.ROOT), failure);
        }

        void setFailure(RuntimeException failure) {
            nextFailure.set(failure);
        }

        /** Makes every subsequent {@link #search} call block until {@link #releaseSearches} is called (or a 10s safety timeout elapses). */
        void blockSearches() {
            blockLatch = new java.util.concurrent.CountDownLatch(1);
        }

        void releaseSearches() {
            java.util.concurrent.CountDownLatch latch = blockLatch;
            if (latch != null) {
                latch.countDown();
            }
        }

        List<ProductCategory> callLog() {
            return List.copyOf(callLog);
        }

        List<String> phraseCallLog() {
            return List.copyOf(phraseCallLog);
        }

        List<RetailProductSearchRequest> requestLog() {
            return List.copyOf(requestLog);
        }

        void resetCallLog() {
            callLog.clear();
            phraseCallLog.clear();
            requestLog.clear();
        }

        void reset() {
            resultsByCategory.clear();
            failuresByCategory.clear();
            resultsByPhrase.clear();
            failuresByPhrase.clear();
            callLog.clear();
            phraseCallLog.clear();
            requestLog.clear();
            nextFailure.set(null);
            releaseSearches();
            blockLatch = null;
        }
    }
}
