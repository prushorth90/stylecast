package com.stylecast.event.styling;

import com.stylecast.common.error.ApiError;
import com.stylecast.event.Event;
import com.stylecast.event.EventRepository;
import com.stylecast.event.EventSetting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class EventStylePreferencesControllerTest {

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

    private UUID eventId;

    @BeforeEach
    void setUp() {
        preferencesRepository.deleteAll();
        eventRepository.deleteAll();

        Event event = eventRepository.save(new Event(
                UUID.randomUUID(),
                "Rooftop birthday party",
                "Casual outdoor birthday celebration",
                "123 Main St, Springfield",
                OffsetDateTime.now().plusDays(1),
                OffsetDateTime.now().plusDays(1).plusHours(3),
                EventSetting.OUTDOOR,
                "Smart casual",
                Instant.now()));
        eventId = event.getId();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private Map<String, Object> validRequestBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("outfitRequest", "I want a navy suit and tie, but not too formal.");
        body.put("maxBudget", "500.00");
        body.put("clothingSize", "M");
        body.put("shoeSize", "10");
        body.put("preferredStyle", "CLASSIC");
        body.put("preferredColors", List.of("navy", "cream"));
        body.put("colorsToAvoid", List.of("bright red"));
        body.put("shoppingDepartment", "MEN");
        return body;
    }

    private HttpEntity<Map<String, Object>> jsonRequest(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    @Test
    void getPreferences_beforeAnySaved_returns404() {
        ResponseEntity<ApiError> response = restTemplate.getForEntity(
                url("/api/events/" + eventId + "/preferences"), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void putPreferences_forExistingEvent_createsAndReturns200() {
        ResponseEntity<PreferencesResponseBody> response = restTemplate.exchange(
                url("/api/events/" + eventId + "/preferences"),
                HttpMethod.PUT,
                jsonRequest(validRequestBody()),
                PreferencesResponseBody.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        PreferencesResponseBody body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.eventId()).isEqualTo(eventId);
        assertThat(body.outfitRequest()).isEqualTo("I want a navy suit and tie, but not too formal.");
        assertThat(new BigDecimal(body.maxBudget())).isEqualByComparingTo("500.00");
        assertThat(body.clothingSize()).isEqualTo("M");
        assertThat(body.shoeSize()).isEqualTo("10");
        assertThat(body.preferredStyle()).isEqualTo("CLASSIC");
        assertThat(body.preferredColors()).containsExactly("navy", "cream");
        assertThat(body.colorsToAvoid()).containsExactly("bright red");
        assertThat(body.shoppingDepartment()).isEqualTo("MEN");

        assertThat(preferencesRepository.findByEventId(eventId)).isPresent();
    }

    @Test
    void getPreferences_afterSaving_returnsSameData() {
        restTemplate.exchange(
                url("/api/events/" + eventId + "/preferences"),
                HttpMethod.PUT,
                jsonRequest(validRequestBody()),
                PreferencesResponseBody.class);

        ResponseEntity<PreferencesResponseBody> response = restTemplate.getForEntity(
                url("/api/events/" + eventId + "/preferences"), PreferencesResponseBody.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().eventId()).isEqualTo(eventId);
    }

    @Test
    void putPreferences_calledTwice_updatesExistingRecordWithoutDuplicate() {
        restTemplate.exchange(
                url("/api/events/" + eventId + "/preferences"),
                HttpMethod.PUT,
                jsonRequest(validRequestBody()),
                PreferencesResponseBody.class);

        Map<String, Object> updatedBody = new HashMap<>(validRequestBody());
        updatedBody.put("maxBudget", "650.00");

        ResponseEntity<PreferencesResponseBody> response = restTemplate.exchange(
                url("/api/events/" + eventId + "/preferences"),
                HttpMethod.PUT,
                jsonRequest(updatedBody),
                PreferencesResponseBody.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new BigDecimal(response.getBody().maxBudget())).isEqualByComparingTo("650.00");
        assertThat(preferencesRepository.findAll()).hasSize(1);
        assertThat(preferencesRepository.findByEventId(eventId)).isPresent();
        assertThat(preferencesRepository.findByEventId(eventId).get().getMaxBudget())
                .isEqualByComparingTo("650.00");
    }

    @Test
    void putPreferences_withUnknownEvent_returns404() {
        UUID unknownEventId = UUID.randomUUID();

        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/events/" + unknownEventId + "/preferences"),
                HttpMethod.PUT,
                jsonRequest(validRequestBody()),
                ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getPreferences_withUnknownEvent_returns404() {
        UUID unknownEventId = UUID.randomUUID();

        ResponseEntity<ApiError> response = restTemplate.getForEntity(
                url("/api/events/" + unknownEventId + "/preferences"), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void putPreferences_withMalformedEventId_returns400() {
        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/events/not-a-uuid/preferences"),
                HttpMethod.PUT,
                jsonRequest(validRequestBody()),
                ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().fieldErrors()).isNull();
    }

    @Test
    void getPreferences_withMalformedEventId_returns400() {
        ResponseEntity<ApiError> response = restTemplate.getForEntity(
                url("/api/events/not-a-uuid/preferences"), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void putPreferences_withZeroBudget_returns400() {
        Map<String, Object> body = new HashMap<>(validRequestBody());
        body.put("maxBudget", "0");

        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/events/" + eventId + "/preferences"),
                HttpMethod.PUT,
                jsonRequest(body),
                ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().fieldErrors())
                .anyMatch(fieldError -> fieldError.field().equals("maxBudget"));
    }

    @Test
    void putPreferences_withNegativeBudget_returns400() {
        Map<String, Object> body = new HashMap<>(validRequestBody());
        body.put("maxBudget", "-10.00");

        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/events/" + eventId + "/preferences"),
                HttpMethod.PUT,
                jsonRequest(body),
                ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void putPreferences_withMissingClothingSize_returns400() {
        Map<String, Object> body = new HashMap<>(validRequestBody());
        body.put("clothingSize", "  ");

        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/events/" + eventId + "/preferences"),
                HttpMethod.PUT,
                jsonRequest(body),
                ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().fieldErrors())
                .anyMatch(fieldError -> fieldError.field().equals("clothingSize"));
    }

    @Test
    void putPreferences_withMissingShoeSize_returns400() {
        Map<String, Object> body = new HashMap<>(validRequestBody());
        body.remove("shoeSize");

        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/events/" + eventId + "/preferences"),
                HttpMethod.PUT,
                jsonRequest(body),
                ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().fieldErrors())
                .anyMatch(fieldError -> fieldError.field().equals("shoeSize"));
    }

    @Test
    void putPreferences_withMissingPreferredStyle_returns400() {
        Map<String, Object> body = new HashMap<>(validRequestBody());
        body.remove("preferredStyle");

        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/events/" + eventId + "/preferences"),
                HttpMethod.PUT,
                jsonRequest(body),
                ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void putPreferences_withMissingShoppingDepartment_returns400() {
        Map<String, Object> body = new HashMap<>(validRequestBody());
        body.remove("shoppingDepartment");

        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/events/" + eventId + "/preferences"),
                HttpMethod.PUT,
                jsonRequest(body),
                ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().fieldErrors())
                .anyMatch(fieldError -> fieldError.field().equals("shoppingDepartment"));
    }

    @Test
    void putPreferences_withOutfitRequestExceedingMaxLength_returns400() {
        Map<String, Object> body = new HashMap<>(validRequestBody());
        body.put("outfitRequest", "a".repeat(2001));

        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/events/" + eventId + "/preferences"),
                HttpMethod.PUT,
                jsonRequest(body),
                ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().fieldErrors())
                .anyMatch(fieldError -> fieldError.field().equals("outfitRequest"));
    }

    /**
     * Minimal shape used only for deserializing responses in this test class.
     */
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
            String updatedAt) {
    }
}
