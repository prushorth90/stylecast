package com.stylecast.occasion;

import com.stylecast.event.EventSetting;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link OpenAiOccasionClassifier}.
 *
 * <p>Response-parsing behavior ({@link OpenAiOccasionClassifier#extractResult})
 * is tested directly against hand-built JSON fixtures shaped like the OpenAI
 * Responses API. HTTP-layer behavior (timeout, error status, malformed body,
 * missing API key) is tested against a local {@link HttpServer} fake -
 * never the real OpenAI API.
 */
class OpenAiOccasionClassifierTest {

    private static final JsonMapper MAPPER = new JsonMapper();

    private final OccasionClassificationInput input = new OccasionClassificationInput(
            "Sarah & Tom's Wedding", "Outdoor garden ceremony", EventSetting.OUTDOOR, null, null, null,
            List.of("navy"), List.of());

    private HttpServer fakeServer;

    @AfterEach
    void stopFakeServer() {
        if (fakeServer != null) {
            fakeServer.stop(0);
        }
    }

    private OpenAiOccasionClassifier classifierWithoutHttp(OccasionClassifierProperties properties) {
        return new OpenAiOccasionClassifier(properties, MAPPER, WebClient.builder());
    }

    // --- extractResult: pure response-parsing logic ---------------------------

    @Test
    void extractResult_withValidOutputText_returnsValidatedResult() {
        JsonNode response = MAPPER.readTree("""
                {
                  "status": "completed",
                  "output": [
                    {
                      "type": "message",
                      "content": [
                        {
                          "type": "output_text",
                          "text": "{\\"occasion\\":\\"WEDDING\\",\\"dressCode\\":\\"GARDEN_COCKTAIL\\",\\"formalityLevel\\":8,\\"requiredCategories\\":[\\"SUIT\\",\\"SHOES\\"],\\"optionalCategories\\":[\\"ACCESSORY\\"],\\"preferredColors\\":[\\"navy\\"],\\"colorsToAvoid\\":[],\\"specialRequirements\\":[\\"OUTDOOR_SUITABLE\\"],\\"assumptions\\":[\\"Outdoor garden wedding implies cocktail-adjacent formality.\\"],\\"confidence\\":0.88}"
                        }
                      ]
                    }
                  ]
                }
                """);

        OccasionClassificationResult result =
                classifierWithoutHttp(properties("key")).extractResult(response);

        assertThat(result.occasion()).isEqualTo(OccasionType.WEDDING);
        assertThat(result.dressCode()).isEqualTo(InterpretedDressCode.GARDEN_COCKTAIL);
        assertThat(result.source()).isEqualTo(InterpretationSource.AI);
        assertThat(result.modelName()).isEqualTo("test-model");
    }

    @Test
    void extractResult_withFailedStatus_throws() {
        JsonNode response = MAPPER.readTree("""
                {
                  "status": "failed",
                  "error": {"message": "content policy violation"}
                }
                """);

        assertThatThrownBy(() -> classifierWithoutHttp(properties("key")).extractResult(response))
                .isInstanceOf(OccasionClassificationException.class)
                .hasMessageContaining("content policy violation");
    }

    @Test
    void extractResult_withNoOutputText_throws() {
        JsonNode response = MAPPER.readTree("""
                {
                  "status": "completed",
                  "output": []
                }
                """);

        assertThatThrownBy(() -> classifierWithoutHttp(properties("key")).extractResult(response))
                .isInstanceOf(OccasionClassificationException.class);
    }

    @Test
    void extractResult_withMalformedInnerJson_throws() {
        JsonNode response = MAPPER.readTree("""
                {
                  "status": "completed",
                  "output": [
                    {
                      "type": "message",
                      "content": [
                        {"type": "output_text", "text": "not valid json {{{"}
                      ]
                    }
                  ]
                }
                """);

        assertThatThrownBy(() -> classifierWithoutHttp(properties("key")).extractResult(response))
                .isInstanceOf(OccasionClassificationException.class);
    }

    @Test
    void extractResult_withInvalidStructuredOutput_throwsAndIsNeverPersisted() {
        JsonNode response = MAPPER.readTree("""
                {
                  "status": "completed",
                  "output": [
                    {
                      "type": "message",
                      "content": [
                        {
                          "type": "output_text",
                          "text": "{\\"occasion\\":\\"WEDDING\\",\\"dressCode\\":\\"COCKTAIL\\",\\"formalityLevel\\":42,\\"requiredCategories\\":[],\\"optionalCategories\\":[],\\"preferredColors\\":[],\\"colorsToAvoid\\":[],\\"specialRequirements\\":[],\\"assumptions\\":[],\\"confidence\\":0.5}"
                        }
                      ]
                    }
                  ]
                }
                """);

        assertThatThrownBy(() -> classifierWithoutHttp(properties("key")).extractResult(response))
                .isInstanceOf(OccasionClassificationException.class)
                .hasMessageContaining("formalityLevel");
    }

    // --- HTTP-layer behavior against a local fake server -----------------------

    @Test
    void classify_withoutApiKey_throwsWithoutMakingHttpCall() {
        OpenAiOccasionClassifier classifier = classifierWithoutHttp(properties(""));

        assertThatThrownBy(() -> classifier.classify(input))
                .isInstanceOf(OccasionClassificationException.class)
                .hasMessageContaining("OPENAI_API_KEY");
    }

    @Test
    void classify_whenFakeServerReturnsValidResponse_returnsResult() throws IOException {
        String body = """
                {
                  "status": "completed",
                  "output": [
                    {
                      "type": "message",
                      "content": [
                        {
                          "type": "output_text",
                          "text": "{\\"occasion\\":\\"WEDDING\\",\\"dressCode\\":\\"GARDEN_COCKTAIL\\",\\"formalityLevel\\":8,\\"requiredCategories\\":[\\"SUIT\\"],\\"optionalCategories\\":[],\\"preferredColors\\":[],\\"colorsToAvoid\\":[],\\"specialRequirements\\":[],\\"assumptions\\":[],\\"confidence\\":0.8}"
                        }
                      ]
                    }
                  ]
                }
                """;
        String baseUrl = startFakeServer(200, body);
        OpenAiOccasionClassifier classifier = classifierWithoutHttp(properties("test-key", baseUrl));

        OccasionClassificationResult result = classifier.classify(input);

        assertThat(result.occasion()).isEqualTo(OccasionType.WEDDING);
        assertThat(result.source()).isEqualTo(InterpretationSource.AI);
    }

    @Test
    void classify_whenFakeServerReturnsErrorStatus_throws() throws IOException {
        String baseUrl = startFakeServer(500, "{\"error\": {\"message\": \"internal error\"}}");
        OpenAiOccasionClassifier classifier = classifierWithoutHttp(properties("test-key", baseUrl));

        assertThatThrownBy(() -> classifier.classify(input))
                .isInstanceOf(OccasionClassificationException.class);
    }

    @Test
    void classify_whenFakeServerReturnsMalformedJson_throws() throws IOException {
        String baseUrl = startFakeServer(200, "not valid json {{{");
        OpenAiOccasionClassifier classifier = classifierWithoutHttp(properties("test-key", baseUrl));

        assertThatThrownBy(() -> classifier.classify(input))
                .isInstanceOf(OccasionClassificationException.class);
    }

    @Test
    void classify_whenFakeServerIsSlowerThanReadTimeout_throws() throws IOException {
        String baseUrl = startSlowFakeServer(2000);
        // Read timeout much shorter than the server's artificial delay above.
        OccasionClassifierProperties shortTimeoutProperties =
                new OccasionClassifierProperties("test-key", "test-model", baseUrl, 200, 200);
        OpenAiOccasionClassifier classifier = classifierWithoutHttp(shortTimeoutProperties);

        assertThatThrownBy(() -> classifier.classify(input))
                .isInstanceOf(OccasionClassificationException.class);
    }

    @Test
    void classify_sendsTheConfiguredModel_neverAHardcodedOrSdkDefault() throws IOException {
        AtomicReference<String> capturedRequestBody = new AtomicReference<>();
        String baseUrl = startFakeServerCapturingRequestBody(200, """
                {"status": "completed", "output": []}
                """, capturedRequestBody);
        OccasionClassifierProperties properties =
                new OccasionClassifierProperties("test-key", "custom-configured-model", baseUrl, 2000, 5000);

        assertThatThrownBy(() -> classifierWithoutHttp(properties).classify(input))
                .isInstanceOf(OccasionClassificationException.class); // empty output -> no valid result, irrelevant here

        JsonNode sentBody = MAPPER.readTree(capturedRequestBody.get());
        assertThat(sentBody.path("model").asString(null)).isEqualTo("custom-configured-model");
    }

    private OccasionClassifierProperties properties(String apiKey) {
        return properties(apiKey, "http://localhost:1");
    }

    private OccasionClassifierProperties properties(String apiKey, String baseUrl) {
        return new OccasionClassifierProperties(apiKey, "test-model", baseUrl, 2000, 5000);
    }

    private String startFakeServer(int status, String responseBody) throws IOException {
        fakeServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        fakeServer.createContext("/responses", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        fakeServer.start();
        return "http://localhost:" + fakeServer.getAddress().getPort();
    }

    private String startFakeServerCapturingRequestBody(int status, String responseBody, AtomicReference<String> captured) throws IOException {
        fakeServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        fakeServer.createContext("/responses", exchange -> {
            captured.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        fakeServer.start();
        return "http://localhost:" + fakeServer.getAddress().getPort();
    }

    private String startSlowFakeServer(long delayMs) throws IOException {
        fakeServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        fakeServer.createContext("/responses", exchange -> {
            try {
                TimeUnit.MILLISECONDS.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = "{\"status\": \"completed\", \"output\": []}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        fakeServer.start();
        return "http://localhost:" + fakeServer.getAddress().getPort();
    }
}
