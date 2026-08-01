package com.stylecast.recommendation;

import com.stylecast.catalog.ProductCategory;
import com.stylecast.catalog.ProductRepository;
import com.stylecast.catalog.WeatherTag;
import com.stylecast.common.error.ApiError;
import com.stylecast.event.Event;
import com.stylecast.event.EventRepository;
import com.stylecast.event.EventSetting;
import com.stylecast.event.styling.EventStylePreferences;
import com.stylecast.event.styling.EventStylePreferencesRepository;
import com.stylecast.event.styling.PreferredStyle;
import com.stylecast.occasion.OccasionInterpretation;
import com.stylecast.occasion.OccasionInterpretationRepository;
import com.stylecast.occasion.OccasionType;
import com.stylecast.recommendation.dto.OutfitItemResponse;
import com.stylecast.recommendation.dto.OutfitRecommendationResponse;
import com.stylecast.recommendation.dto.RecommendationsResponse;
import com.stylecast.weather.EventWeatherSnapshotRepository;
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
 * Full-request tests for {@code POST /api/events/{eventId}/recommendations/generate}
 * and {@code GET /api/events/{eventId}/recommendations}, exercising the
 * deterministic engine end-to-end against the real seeded catalog
 * (Testcontainers Postgres, same pattern as {@code OccasionInterpretationControllerTest}).
 *
 * <p>No bean in this module depends on {@code WebClient}, the OpenAI API, or
 * {@code com.stylecast.retail} - every recommendation returned by these
 * tests has {@code source() == RecommendationSource.LOCAL_CATALOG},
 * structurally confirming no live provider or LLM call ever occurs.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
@ActiveProfiles("test")
@Import(NoExternalNetworkGuardConfig.class)
class RecommendationControllerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    // Known seeded catalog fixtures (see V7__seed_catalog_data.sql) used to assert
    // exclusion rules against real, stable data rather than hand-built fixtures.
    private static final UUID INACTIVE_PRODUCT_ID = UUID.fromString("17a8cdf3-0723-529c-98c8-b108bf9d01bf");
    private static final UUID SOLD_OUT_PRODUCT_ID = UUID.fromString("9a5ab631-8abe-5685-b14f-d9ff2818be23");

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
    private OutfitRecommendationRepository recommendationRepository;

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void cleanDatabase() {
        recommendationRepository.deleteAll();
        weatherSnapshotRepository.deleteAll();
        interpretationRepository.deleteAll();
        preferencesRepository.deleteAll();
        eventRepository.deleteAll();
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

    private void savePreferences(UUID eventId, BigDecimal maxBudget, List<String> colorsToAvoid) {
        EventStylePreferences preferences = new EventStylePreferences(UUID.randomUUID(), eventId, Instant.now());
        preferences.apply("Something stylish", maxBudget, "M", "9", PreferredStyle.CLASSIC, List.of(), colorsToAvoid, Instant.now());
        preferencesRepository.save(preferences);
    }

    private void saveInterpretation(
            UUID eventId, OccasionType occasion, int formalityLevel, List<ProductCategory> requiredCategories,
            List<ProductCategory> optionalCategories, List<String> colorsToAvoid) {
        OccasionInterpretation interpretation = RecommendationFixtures.interpretation(
                eventId, occasion, formalityLevel, requiredCategories, optionalCategories, colorsToAvoid);
        interpretationRepository.save(interpretation);
    }

    private ResponseEntity<RecommendationsResponse> generate(UUID eventId) {
        return restTemplate.postForEntity(
                url("/api/events/" + eventId + "/recommendations/generate"), null, RecommendationsResponse.class);
    }

    private ResponseEntity<RecommendationsResponse> getRecommendations(UUID eventId) {
        return restTemplate.getForEntity(
                url("/api/events/" + eventId + "/recommendations"), RecommendationsResponse.class);
    }

    // --- Validation and prerequisite errors ---------------------------------------

    @Test
    void generate_withMalformedEventId_returns400() {
        ResponseEntity<ApiError> response = restTemplate.postForEntity(
                url("/api/events/not-a-uuid/recommendations/generate"), null, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getRecommendations_withMalformedEventId_returns400() {
        ResponseEntity<ApiError> response =
                restTemplate.getForEntity(url("/api/events/not-a-uuid/recommendations"), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void generate_withUnknownEventId_returns404() {
        ResponseEntity<ApiError> response = restTemplate.postForEntity(
                url("/api/events/" + UUID.randomUUID() + "/recommendations/generate"), null, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getRecommendations_withUnknownEventId_returns404() {
        ResponseEntity<ApiError> response =
                restTemplate.getForEntity(url("/api/events/" + UUID.randomUUID() + "/recommendations"), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void generate_withoutSavedPreferences_returns409() {
        UUID eventId = createEvent("Networking Mixer");

        ResponseEntity<ApiError> response = restTemplate.postForEntity(
                url("/api/events/" + eventId + "/recommendations/generate"), null, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void generate_withoutOccasionInterpretation_returns409() {
        UUID eventId = createEvent("Networking Mixer");
        savePreferences(eventId, BigDecimal.valueOf(1500), List.of());

        ResponseEntity<ApiError> response = restTemplate.postForEntity(
                url("/api/events/" + eventId + "/recommendations/generate"), null, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // --- Valid generation scenarios --------------------------------------------

    @Test
    void generate_validWeddingOutfit_satisfiesEveryHardConstraint() {
        UUID eventId = createEvent("Sarah & Tom's Wedding");
        BigDecimal budget = BigDecimal.valueOf(2000);
        savePreferences(eventId, budget, List.of());
        saveInterpretation(eventId, OccasionType.WEDDING, 9,
                List.of(ProductCategory.SUIT), List.of(ProductCategory.ACCESSORY), List.of());

        ResponseEntity<RecommendationsResponse> response = generate(eventId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        RecommendationsResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.hasResults()).isTrue();
        assertThat(body.recommendations()).isNotEmpty().hasSizeLessThanOrEqualTo(3);
        assertEveryHardConstraintSatisfied(body, budget, "M", "9", List.of(), List.of(ProductCategory.SUIT, ProductCategory.SHOES));
    }

    @Test
    void generate_validInterviewOutfit_satisfiesEveryHardConstraint() {
        UUID eventId = createEvent("Product Manager Interview");
        BigDecimal budget = BigDecimal.valueOf(1200);
        savePreferences(eventId, budget, List.of());
        saveInterpretation(eventId, OccasionType.INTERVIEW, 8,
                List.of(ProductCategory.SUIT), List.of(), List.of());

        ResponseEntity<RecommendationsResponse> response = generate(eventId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        RecommendationsResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.hasResults()).isTrue();
        assertEveryHardConstraintSatisfied(body, budget, "M", "9", List.of(), List.of(ProductCategory.SUIT, ProductCategory.SHOES));
    }

    @Test
    void generate_excludesAvoidedColor() {
        UUID eventId = createEvent("Sarah & Tom's Wedding");
        savePreferences(eventId, BigDecimal.valueOf(2000), List.of("Navy"));
        saveInterpretation(eventId, OccasionType.WEDDING, 9,
                List.of(ProductCategory.SUIT), List.of(ProductCategory.ACCESSORY), List.of());

        RecommendationsResponse body = generate(eventId).getBody();

        assertThat(body).isNotNull();
        assertThat(body.hasResults()).isTrue();
        for (OutfitRecommendationResponse outfit : body.recommendations()) {
            assertThat(outfit.items()).noneMatch(item -> item.color().equalsIgnoreCase("Navy"));
        }
    }

    @Test
    void generate_neverIncludesInactiveOrOutOfStockProducts() {
        UUID eventId = createEvent("Casual Networking Dinner");
        savePreferences(eventId, BigDecimal.valueOf(2000), List.of());
        saveInterpretation(eventId, OccasionType.NETWORKING, 6,
                List.of(ProductCategory.BLAZER), List.of(ProductCategory.OUTERWEAR), List.of());

        RecommendationsResponse body = generate(eventId).getBody();

        assertThat(body).isNotNull();
        for (OutfitRecommendationResponse outfit : body.recommendations()) {
            assertThat(outfit.items()).noneMatch(item -> item.productId().equals(INACTIVE_PRODUCT_ID));
            assertThat(outfit.items()).noneMatch(item -> item.productId().equals(SOLD_OUT_PRODUCT_ID));
        }
    }

    @Test
    void generate_noResultResponse_whenBudgetIsImpossiblyLow() {
        UUID eventId = createEvent("Sarah & Tom's Wedding");
        savePreferences(eventId, BigDecimal.valueOf(1), List.of());
        saveInterpretation(eventId, OccasionType.WEDDING, 9,
                List.of(ProductCategory.SUIT), List.of(), List.of());

        ResponseEntity<RecommendationsResponse> response = generate(eventId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        RecommendationsResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.hasResults()).isFalse();
        assertThat(body.recommendations()).isEmpty();
        assertThat(body.noResultReason()).isNotBlank();
    }

    @Test
    void generate_weatherDoesNotFabricateAConstraintWhenMissing() {
        UUID eventId = createEvent("Sarah & Tom's Wedding");
        savePreferences(eventId, BigDecimal.valueOf(2000), List.of());
        saveInterpretation(eventId, OccasionType.WEDDING, 9,
                List.of(ProductCategory.SUIT), List.of(), List.of());
        // Deliberately no weather snapshot saved at all.

        RecommendationsResponse body = generate(eventId).getBody();

        assertThat(body).isNotNull();
        assertThat(body.hasResults()).isTrue();
        assertThat(body.recommendations()).allSatisfy(outfit -> assertThat(outfit.weatherFitScore()).isBetween(0, 100));
    }

    @Test
    void generate_coldWeatherNeverSelectsAHotOnlyTaggedProduct() {
        UUID eventId = createEvent("Sarah & Tom's Wedding");
        savePreferences(eventId, BigDecimal.valueOf(2000), List.of());
        saveInterpretation(eventId, OccasionType.WEDDING, 9,
                List.of(ProductCategory.SUIT), List.of(ProductCategory.OUTERWEAR), List.of());
        weatherSnapshotRepository.save(RecommendationFixtures.availableWeather(eventId, -2.0, 10, 5.0));

        RecommendationsResponse body = generate(eventId).getBody();

        assertThat(body).isNotNull();
        for (OutfitRecommendationResponse outfit : body.recommendations()) {
            for (OutfitItemResponse item : outfit.items()) {
                var product = restTemplate.getForEntity(
                        url("/api/products/" + item.productId()), com.stylecast.catalog.dto.ProductDetailResponse.class).getBody();
                assertThat(product).isNotNull();
                boolean hotOnly = product.weatherTags().contains(WeatherTag.HOT)
                        && !product.weatherTags().contains(WeatherTag.COLD)
                        && !product.weatherTags().contains(WeatherTag.MILD);
                assertThat(hotOnly).isFalse();
            }
        }
    }

    @Test
    void generate_noDuplicateItemsWithinAnOutfit() {
        UUID eventId = createEvent("Sarah & Tom's Wedding");
        savePreferences(eventId, BigDecimal.valueOf(2000), List.of());
        saveInterpretation(eventId, OccasionType.WEDDING, 9,
                List.of(ProductCategory.SUIT), List.of(ProductCategory.ACCESSORY), List.of());

        RecommendationsResponse body = generate(eventId).getBody();

        assertThat(body).isNotNull();
        for (OutfitRecommendationResponse outfit : body.recommendations()) {
            long distinctProducts = outfit.items().stream().map(OutfitItemResponse::productId).distinct().count();
            assertThat(distinctProducts).isEqualTo(outfit.items().size());
        }
    }

    @Test
    void generate_returnsUpToThreeDistinctOutfitsRankedByOverallScoreDescending() {
        UUID eventId = createEvent("Sarah & Tom's Wedding");
        savePreferences(eventId, BigDecimal.valueOf(2500), List.of());
        saveInterpretation(eventId, OccasionType.WEDDING, 9,
                List.of(ProductCategory.SUIT), List.of(ProductCategory.ACCESSORY, ProductCategory.OUTERWEAR), List.of());

        RecommendationsResponse body = generate(eventId).getBody();

        assertThat(body).isNotNull();
        assertThat(body.recommendations()).hasSizeLessThanOrEqualTo(3);

        List<Integer> scores = body.recommendations().stream().map(OutfitRecommendationResponse::overallScore).toList();
        assertThat(scores).isSortedAccordingTo(java.util.Comparator.reverseOrder());

        List<String> itemSetKeys = body.recommendations().stream()
                .map(outfit -> outfit.items().stream().map(item -> item.productId().toString()).sorted().toList().toString())
                .toList();
        assertThat(itemSetKeys).doesNotHaveDuplicates();

        assertThat(body.recommendations()).allSatisfy(outfit -> assertThat(outfit.source()).isEqualTo(RecommendationSource.LOCAL_CATALOG));
    }

    // --- GET / regeneration behavior -------------------------------------------

    @Test
    void getRecommendations_beforeAnyGeneration_returnsNotGeneratedYetWithoutError() {
        UUID eventId = createEvent("Sarah & Tom's Wedding");
        savePreferences(eventId, BigDecimal.valueOf(2000), List.of());
        saveInterpretation(eventId, OccasionType.WEDDING, 9, List.of(ProductCategory.SUIT), List.of(), List.of());

        ResponseEntity<RecommendationsResponse> response = getRecommendations(eventId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        RecommendationsResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.hasResults()).isFalse();
        assertThat(body.generation()).isZero();
        assertThat(recommendationRepository.count()).isZero();
    }

    @Test
    void getRecommendations_repeatedly_neverRegenerates() {
        UUID eventId = createEvent("Sarah & Tom's Wedding");
        savePreferences(eventId, BigDecimal.valueOf(2000), List.of());
        saveInterpretation(eventId, OccasionType.WEDDING, 9, List.of(ProductCategory.SUIT), List.of(), List.of());

        RecommendationsResponse generated = generate(eventId).getBody();
        assertThat(generated).isNotNull();
        long countAfterGenerate = recommendationRepository.count();

        RecommendationsResponse first = getRecommendations(eventId).getBody();
        RecommendationsResponse second = getRecommendations(eventId).getBody();

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(first.generation()).isEqualTo(generated.generation());
        assertThat(second.generation()).isEqualTo(generated.generation());
        assertThat(recommendationRepository.count()).isEqualTo(countAfterGenerate);
    }

    @Test
    void regenerate_incrementsGenerationAndSupersedesThePreviousOne() {
        UUID eventId = createEvent("Sarah & Tom's Wedding");
        savePreferences(eventId, BigDecimal.valueOf(2000), List.of());
        saveInterpretation(eventId, OccasionType.WEDDING, 9, List.of(ProductCategory.SUIT), List.of(), List.of());

        RecommendationsResponse first = generate(eventId).getBody();
        assertThat(first).isNotNull();
        assertThat(first.hasResults()).isTrue();

        RecommendationsResponse second = generate(eventId).getBody();
        assertThat(second).isNotNull();

        assertThat(second.generation()).isEqualTo(first.generation() + 1);

        List<OutfitRecommendation> previousGenerationRows = recommendationRepository.findAll().stream()
                .filter(row -> row.getGeneration() == first.generation())
                .toList();
        assertThat(previousGenerationRows).isNotEmpty();
        assertThat(previousGenerationRows).allSatisfy(
                row -> assertThat(row.getStatus()).isEqualTo(RecommendationStatus.SUPERSEDED));

        RecommendationsResponse current = getRecommendations(eventId).getBody();
        assertThat(current).isNotNull();
        assertThat(current.generation()).isEqualTo(second.generation());
    }

    private void assertEveryHardConstraintSatisfied(
            RecommendationsResponse response, BigDecimal budget, String clothingSize, String shoeSize,
            List<String> colorsToAvoid, List<ProductCategory> requiredCategories) {
        for (OutfitRecommendationResponse outfit : response.recommendations()) {
            assertThat(outfit.totalPrice()).isLessThanOrEqualTo(budget);

            BigDecimal sumOfItems = outfit.items().stream().map(OutfitItemResponse::itemPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(outfit.totalPrice()).isEqualByComparingTo(sumOfItems);

            List<ProductCategory> categories = outfit.items().stream().map(OutfitItemResponse::category).toList();
            for (ProductCategory required : requiredCategories) {
                boolean satisfied = categories.contains(required)
                        || (required == ProductCategory.SUIT && categories.contains(ProductCategory.BLAZER))
                        || (required == ProductCategory.DRESS && categories.contains(ProductCategory.SKIRT));
                assertThat(satisfied)
                        .as("outfit should satisfy required category %s (or its template substitute), items were %s", required, categories)
                        .isTrue();
            }

            long distinctProducts = outfit.items().stream().map(OutfitItemResponse::productId).distinct().count();
            assertThat(distinctProducts).isEqualTo(outfit.items().size());

            for (OutfitItemResponse item : outfit.items()) {
                if (item.category() == ProductCategory.SHOES) {
                    assertThat(item.size()).isEqualToIgnoringCase(shoeSize);
                } else if (item.category() != ProductCategory.ACCESSORY) {
                    assertThat(item.size()).isEqualToIgnoringCase(clothingSize);
                }
                for (String avoided : colorsToAvoid) {
                    assertThat(item.color()).isNotEqualToIgnoringCase(avoided);
                }
                var product = productRepository.findById(item.productId()).orElseThrow();
                assertThat(product.isActive()).isTrue();
            }

            assertThat(outfit.source()).isEqualTo(RecommendationSource.LOCAL_CATALOG);
        }
    }
}
