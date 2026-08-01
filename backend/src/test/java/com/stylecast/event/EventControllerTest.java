package com.stylecast.event;

import com.stylecast.common.error.ApiError;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
@ActiveProfiles("test")
@Import(NoExternalNetworkGuardConfig.class)
class EventControllerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private EventRepository eventRepository;

    private TestAuthSupport.InstalledAuth auth;

    @BeforeEach
    void cleanDatabase() {
        eventRepository.deleteAll();
    }

    @BeforeEach
    void authenticate() {
        auth = TestAuthSupport.installAuthenticatedUser(restTemplate, port);
    }

    @AfterEach
    void clearAuthentication() {
        TestAuthSupport.uninstall(restTemplate, auth);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private Map<String, Object> validRequestBody() {
        OffsetDateTime start = OffsetDateTime.now().plusDays(1);
        return Map.of(
                "title", "Rooftop birthday party",
                "description", "Casual outdoor birthday celebration",
                "location", "123 Main St, Springfield",
                "startTime", start.toString(),
                "endTime", start.plusHours(3).toString(),
                "setting", "OUTDOOR",
                "dressCode", "Smart casual");
    }

    private HttpEntity<Map<String, Object>> jsonRequest(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    @Test
    void createEvent_withValidRequest_returns201AndPersists() {
        ResponseEntity<EventResponseBody> response = restTemplate.postForEntity(
                url("/api/events"), jsonRequest(validRequestBody()), EventResponseBody.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).isNotNull();
        EventResponseBody created = response.getBody();
        assertThat(created).isNotNull();
        assertThat(created.id()).isNotNull();
        assertThat(created.title()).isEqualTo("Rooftop birthday party");
        assertThat(created.location()).isEqualTo("123 Main St, Springfield");
        assertThat(created.setting()).isEqualTo("OUTDOOR");
        assertThat(created.dressCode()).isEqualTo("Smart casual");
        assertThat(created.createdAt()).isNotNull();

        assertThat(eventRepository.findById(created.id())).isPresent();
    }

    @Test
    void createEvent_withBlankTitle_returns400() {
        Map<String, Object> body = new java.util.HashMap<>(validRequestBody());
        body.put("title", "   ");

        ResponseEntity<ApiError> response = restTemplate.postForEntity(
                url("/api/events"), jsonRequest(body), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().fieldErrors())
                .anyMatch(fieldError -> fieldError.field().equals("title"));
    }

    @Test
    void createEvent_withBlankLocation_returns400() {
        Map<String, Object> body = new java.util.HashMap<>(validRequestBody());
        body.put("location", "");

        ResponseEntity<ApiError> response = restTemplate.postForEntity(
                url("/api/events"), jsonRequest(body), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().fieldErrors())
                .anyMatch(fieldError -> fieldError.field().equals("location"));
    }

    @Test
    void createEvent_withMissingSetting_returns400() {
        Map<String, Object> body = new java.util.HashMap<>(validRequestBody());
        body.remove("setting");

        ResponseEntity<ApiError> response = restTemplate.postForEntity(
                url("/api/events"), jsonRequest(body), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createEvent_withEndBeforeStart_returns400() {
        OffsetDateTime start = OffsetDateTime.now().plusDays(1);
        Map<String, Object> body = new java.util.HashMap<>(validRequestBody());
        body.put("startTime", start.toString());
        body.put("endTime", start.minusHours(1).toString());

        ResponseEntity<ApiError> response = restTemplate.postForEntity(
                url("/api/events"), jsonRequest(body), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("endTime must be after startTime");
    }

    @Test
    void createEvent_withEndEqualToStart_returns400() {
        OffsetDateTime start = OffsetDateTime.now().plusDays(1);
        Map<String, Object> body = new java.util.HashMap<>(validRequestBody());
        body.put("startTime", start.toString());
        body.put("endTime", start.toString());

        ResponseEntity<ApiError> response = restTemplate.postForEntity(
                url("/api/events"), jsonRequest(body), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void listEvents_returnsUpcomingEventsInChronologicalOrder() {
        OffsetDateTime now = OffsetDateTime.now();
        Event soonest = eventRepository.save(sampleEvent("Soonest", now.plusHours(2), now.plusHours(3)));
        Event latest = eventRepository.save(sampleEvent("Latest", now.plusDays(5), now.plusDays(5).plusHours(1)));
        Event middle = eventRepository.save(sampleEvent("Middle", now.plusDays(1), now.plusDays(1).plusHours(1)));

        ResponseEntity<EventResponseBody[]> response = restTemplate.getForEntity(
                url("/api/events"), EventResponseBody[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<UUID> orderedIds = List.of(response.getBody()).stream().map(EventResponseBody::id).toList();
        assertThat(orderedIds).containsExactly(soonest.getId(), middle.getId(), latest.getId());
    }

    @Test
    void listEvents_excludesPastEvents() {
        OffsetDateTime now = OffsetDateTime.now();
        Event past = eventRepository.save(sampleEvent("Past event", now.minusDays(2), now.minusDays(2).plusHours(1)));
        Event upcoming = eventRepository.save(sampleEvent("Upcoming event", now.plusDays(1), now.plusDays(1).plusHours(1)));

        ResponseEntity<EventResponseBody[]> response = restTemplate.getForEntity(
                url("/api/events"), EventResponseBody[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<UUID> ids = List.of(response.getBody()).stream().map(EventResponseBody::id).toList();
        assertThat(ids).contains(upcoming.getId());
        assertThat(ids).doesNotContain(past.getId());
    }

    @Test
    void getEvent_withExistingId_returnsEvent() {
        Event event = eventRepository.save(sampleEvent(
                "Existing event", OffsetDateTime.now().plusDays(1), OffsetDateTime.now().plusDays(1).plusHours(1)));

        ResponseEntity<EventResponseBody> response = restTemplate.getForEntity(
                url("/api/events/" + event.getId()), EventResponseBody.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isEqualTo(event.getId());
        assertThat(response.getBody().title()).isEqualTo("Existing event");
    }

    @Test
    void getEvent_withUnknownId_returns404() {
        UUID unknownId = UUID.randomUUID();

        ResponseEntity<ApiError> response = restTemplate.getForEntity(
                url("/api/events/" + unknownId), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains(unknownId.toString());
    }

    @Test
    void getEvent_withMalformedId_returns400() {
        ResponseEntity<ApiError> response = restTemplate.getForEntity(url("/api/events/1"), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().fieldErrors()).isNull();
    }

    @Test
    void updateEvent_forExistingEvent_updatesFieldsAndKeepsSameId() {
        Event event = eventRepository.save(sampleEvent(
                "Draft title", OffsetDateTime.now().plusDays(1), OffsetDateTime.now().plusDays(1).plusHours(1)));

        OffsetDateTime newStart = OffsetDateTime.now().plusDays(2);
        Map<String, Object> body = new java.util.HashMap<>(validRequestBody());
        body.put("title", "Updated title");
        body.put("startTime", newStart.toString());
        body.put("endTime", newStart.plusHours(2).toString());

        ResponseEntity<EventResponseBody> response = restTemplate.exchange(
                url("/api/events/" + event.getId()),
                org.springframework.http.HttpMethod.PUT,
                jsonRequest(body),
                EventResponseBody.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isEqualTo(event.getId());
        assertThat(response.getBody().title()).isEqualTo("Updated title");

        assertThat(eventRepository.findAll()).hasSize(1);
        assertThat(eventRepository.findById(event.getId())).isPresent();
        assertThat(eventRepository.findById(event.getId()).get().getTitle()).isEqualTo("Updated title");
    }

    @Test
    void updateEvent_calledRepeatedly_neverCreatesADuplicate() {
        Event event = eventRepository.save(sampleEvent(
                "Draft title", OffsetDateTime.now().plusDays(1), OffsetDateTime.now().plusDays(1).plusHours(1)));

        for (int i = 0; i < 3; i++) {
            Map<String, Object> body = new java.util.HashMap<>(validRequestBody());
            body.put("title", "Edit #" + i);
            restTemplate.exchange(
                    url("/api/events/" + event.getId()),
                    org.springframework.http.HttpMethod.PUT,
                    jsonRequest(body),
                    EventResponseBody.class);
        }

        assertThat(eventRepository.findAll()).hasSize(1);
        assertThat(eventRepository.findById(event.getId()).get().getTitle()).isEqualTo("Edit #2");
    }

    @Test
    void updateEvent_withUnknownId_returns404() {
        UUID unknownId = UUID.randomUUID();

        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/events/" + unknownId),
                org.springframework.http.HttpMethod.PUT,
                jsonRequest(validRequestBody()),
                ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateEvent_withEndBeforeStart_returns400() {
        Event event = eventRepository.save(sampleEvent(
                "Draft title", OffsetDateTime.now().plusDays(1), OffsetDateTime.now().plusDays(1).plusHours(1)));

        OffsetDateTime start = OffsetDateTime.now().plusDays(1);
        Map<String, Object> body = new java.util.HashMap<>(validRequestBody());
        body.put("startTime", start.toString());
        body.put("endTime", start.minusHours(1).toString());

        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/events/" + event.getId()),
                org.springframework.http.HttpMethod.PUT,
                jsonRequest(body),
                ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getEvent_ownedByAnotherUser_returns404() {
        TestAuthSupport.AuthenticatedTestUser otherUser = TestAuthSupport.registerAndLogin(restTemplate, port);
        Event othersEvent = eventRepository.save(new Event(
                UUID.randomUUID(),
                otherUser.userId(),
                "Someone else's event",
                "Description",
                "Some location",
                OffsetDateTime.now().plusDays(1),
                OffsetDateTime.now().plusDays(1).plusHours(1),
                EventSetting.INDOOR,
                "Casual",
                Instant.now()));

        ResponseEntity<ApiError> response = restTemplate.getForEntity(
                url("/api/events/" + othersEvent.getId()), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateEvent_ownedByAnotherUser_returns404() {
        TestAuthSupport.AuthenticatedTestUser otherUser = TestAuthSupport.registerAndLogin(restTemplate, port);
        Event othersEvent = eventRepository.save(new Event(
                UUID.randomUUID(),
                otherUser.userId(),
                "Someone else's event",
                "Description",
                "Some location",
                OffsetDateTime.now().plusDays(1),
                OffsetDateTime.now().plusDays(1).plusHours(1),
                EventSetting.INDOOR,
                "Casual",
                Instant.now()));

        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/events/" + othersEvent.getId()),
                org.springframework.http.HttpMethod.PUT,
                jsonRequest(validRequestBody()),
                ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(eventRepository.findById(othersEvent.getId()).orElseThrow().getTitle())
                .isEqualTo("Someone else's event");
    }

    @Test
    void listEvents_returnsOnlyTheCallingUsersEvents() {
        OffsetDateTime start = OffsetDateTime.now().plusDays(1);
        Event ownEvent = eventRepository.save(sampleEvent("My event", start, start.plusHours(1)));

        TestAuthSupport.AuthenticatedTestUser otherUser = TestAuthSupport.registerAndLogin(restTemplate, port);
        eventRepository.save(new Event(
                UUID.randomUUID(),
                otherUser.userId(),
                "Someone else's event",
                "Description",
                "Some location",
                start,
                start.plusHours(1),
                EventSetting.INDOOR,
                "Casual",
                Instant.now()));

        ResponseEntity<EventResponseBody[]> response = restTemplate.getForEntity(
                url("/api/events"), EventResponseBody[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<UUID> ids = List.of(response.getBody()).stream().map(EventResponseBody::id).toList();
        assertThat(ids).containsExactly(ownEvent.getId());
    }

    private Event sampleEvent(String title, OffsetDateTime start, OffsetDateTime end) {
        return new Event(
                UUID.randomUUID(),
                auth.userId(),
                title,
                "Description for " + title,
                "Some location",
                start,
                end,
                EventSetting.INDOOR,
                "Casual",
                Instant.now());
    }

    /**
     * Minimal shape used only for deserializing responses in this test class.
     */
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
}
