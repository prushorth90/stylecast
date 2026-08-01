package com.stylecast.retail;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link OpenAiProductDetailEnricher}.
 *
 * <p>Response-normalization/validation logic ({@link
 * OpenAiProductDetailEnricher#extractDetails}) is tested directly against
 * hand-built JSON fixtures shaped like the OpenAI Responses API - the same
 * fake-structured-response pattern already used by {@link
 * OpenAiNordstromProductSearchProviderTest}. HTTP-layer behavior (timeout,
 * error status, malformed body) is tested against a local {@link
 * HttpServer} fake. Neither ever calls a real OpenAI endpoint or
 * nordstrom.com.
 *
 * <p>Live Nordstrom product images are no longer fetched or enriched at
 * all (no authorized product feed is available yet - see docs/ROADMAP.md):
 * {@link #extractDetails_neverPopulatesImageUrlFromAiResponseRegardlessOfWhatItReturns}
 * and {@link #enrich_makesExactlyOneHttpRequestPerCallNeverASecondRequestForAnImage}
 * confirm {@code imageUrl} is always {@code null} and that {@link
 * OpenAiProductDetailEnricher#enrich} never makes more than one outbound
 * HTTP request.
 */
class OpenAiProductDetailEnricherTest {

    private static final JsonMapper MAPPER = new JsonMapper();
    private static final String PRODUCT_URL = "https://www.nordstrom.com/s/navy-wedding-suit/1234567";

    private HttpServer fakeServer;

    @AfterEach
    void stopFakeServer() {
        if (fakeServer != null) {
            fakeServer.stop(0);
        }
    }

    private OpenAiProductDetailEnricher enricher(RetailSearchProperties properties) {
        return new OpenAiProductDetailEnricher(properties, MAPPER, WebClient.builder());
    }

    private RetailSearchProperties properties(String apiKey, String baseUrl) {
        return new RetailSearchProperties(apiKey, "test-model", baseUrl, 2000, 5000, 25, 4);
    }

    // --- extractDetails: pure response-normalization/validation logic -------

    @Test
    void extractDetails_whenGroundedAndFullyValid_returnsAllFieldsExceptImageUrl() {
        JsonNode response = groundedResponse("""
                {"brand":"Acme","name":"Verified Navy Suit","price":299.99,"originalPrice":349.99,\
                "currency":"usd","imageUrl":"https://images.nordstrom.com/suit.jpg","color":"Navy",\
                "availableSizes":["40R","42R"],"stockText":"In stock"}""");

        Optional<ProductPageDetails> result = enricher(properties("key", "http://localhost:1")).extractDetails(response, PRODUCT_URL);

        assertThat(result).isPresent();
        ProductPageDetails details = result.get();
        assertThat(details.brand()).isEqualTo("Acme");
        assertThat(details.name()).isEqualTo("Verified Navy Suit");
        assertThat(details.price()).isEqualByComparingTo("299.99");
        assertThat(details.originalPrice()).isEqualByComparingTo("349.99");
        assertThat(details.currency()).isEqualTo("USD");
        // imageUrl is never populated from the AI response, even though this fixture includes one -
        // live Nordstrom images are no longer enriched at all (see class docs).
        assertThat(details.imageUrl()).isNull();
        assertThat(details.color()).isEqualTo("Navy");
        assertThat(details.availableSizes()).containsExactly("40R", "42R");
        assertThat(details.stockText()).isEqualTo("In stock");
    }

    @Test
    void extractDetails_withMensDepartment_classifiesAsMen() {
        JsonNode response = groundedResponse("""
                {"brand":null,"name":null,"price":null,"originalPrice":null,"currency":null,\
                "imageUrl":null,"color":null,"availableSizes":[],"stockText":null,"department":"men"}""");

        Optional<ProductPageDetails> result = enricher(properties("key", "http://localhost:1")).extractDetails(response, PRODUCT_URL);

        assertThat(result).isPresent();
        assertThat(result.get().audience()).isEqualTo(CandidateAudience.MEN);
    }

    @Test
    void extractDetails_withWomensDepartment_classifiesAsWomen() {
        JsonNode response = groundedResponse("""
                {"brand":null,"name":null,"price":null,"originalPrice":null,"currency":null,\
                "imageUrl":null,"color":null,"availableSizes":[],"stockText":null,"department":"women"}""");

        Optional<ProductPageDetails> result = enricher(properties("key", "http://localhost:1")).extractDetails(response, PRODUCT_URL);

        assertThat(result).isPresent();
        assertThat(result.get().audience()).isEqualTo(CandidateAudience.WOMEN);
    }

    @Test
    void extractDetails_withUnisexDepartment_classifiesAsUnisex() {
        JsonNode response = groundedResponse("""
                {"brand":null,"name":null,"price":null,"originalPrice":null,"currency":null,\
                "imageUrl":null,"color":null,"availableSizes":[],"stockText":null,"department":"unisex"}""");

        Optional<ProductPageDetails> result = enricher(properties("key", "http://localhost:1")).extractDetails(response, PRODUCT_URL);

        assertThat(result).isPresent();
        assertThat(result.get().audience()).isEqualTo(CandidateAudience.UNISEX);
    }

    @Test
    void extractDetails_withNullDepartment_leavesAudienceNullRatherThanGuessing() {
        JsonNode response = groundedResponse("""
                {"brand":"Acme","name":"Suit","price":null,"originalPrice":null,"currency":null,\
                "imageUrl":null,"color":null,"availableSizes":[],"stockText":null,"department":null}""");

        Optional<ProductPageDetails> result = enricher(properties("key", "http://localhost:1")).extractDetails(response, PRODUCT_URL);

        assertThat(result).isPresent();
        assertThat(result.get().audience()).isNull();
    }

    @Test
    void extractDetails_withUnrecognizedDepartmentValue_leavesAudienceNullRatherThanGuessing() {
        JsonNode response = groundedResponse("""
                {"brand":"Acme","name":"Suit","price":null,"originalPrice":null,"currency":null,\
                "imageUrl":null,"color":null,"availableSizes":[],"stockText":null,"department":"nonbinary-athleisure"}""");

        Optional<ProductPageDetails> result = enricher(properties("key", "http://localhost:1")).extractDetails(response, PRODUCT_URL);

        assertThat(result).isPresent();
        assertThat(result.get().audience()).isNull();
    }

    @Test
    void extractDetails_whenNotGroundedToRequestedUrl_returnsEmpty() {
        // The citation points at a different product than the one we asked about -
        // whatever the model claims cannot be trusted.
        JsonNode response = responseWithCitationAndText(
                "https://www.nordstrom.com/s/some-other-product/9999999",
                "{\"brand\":\"Acme\",\"name\":\"Suit\",\"price\":100,\"originalPrice\":null,"
                        + "\"currency\":\"USD\",\"imageUrl\":null,\"color\":null,\"availableSizes\":[],\"stockText\":null}");

        Optional<ProductPageDetails> result = enricher(properties("key", "http://localhost:1")).extractDetails(response, PRODUCT_URL);

        assertThat(result).isEmpty();
    }

    @Test
    void extractDetails_whenOutputTextIsNotValidJson_returnsEmpty() {
        JsonNode response = groundedResponse("I could not find structured data on this page.");

        Optional<ProductPageDetails> result = enricher(properties("key", "http://localhost:1")).extractDetails(response, PRODUCT_URL);

        assertThat(result).isEmpty();
    }

    @Test
    void extractDetails_whenEveryFieldIsNull_returnsEmpty() {
        JsonNode response = groundedResponse("""
                {"brand":null,"name":null,"price":null,"originalPrice":null,"currency":null,\
                "imageUrl":null,"color":null,"availableSizes":[],"stockText":null}""");

        Optional<ProductPageDetails> result = enricher(properties("key", "http://localhost:1")).extractDetails(response, PRODUCT_URL);

        assertThat(result).isEmpty();
    }

    @Test
    void extractDetails_withFailedStatus_returnsEmpty() {
        JsonNode response = MAPPER.readTree("""
                {"status": "failed", "error": {"message": "boom"}}
                """);

        Optional<ProductPageDetails> result = enricher(properties("key", "http://localhost:1")).extractDetails(response, PRODUCT_URL);

        assertThat(result).isEmpty();
    }

    @Test
    void extractDetails_withNonPositivePrice_discardsPriceButKeepsOtherFields() {
        JsonNode response = groundedResponse("""
                {"brand":"Acme","name":null,"price":-5,"originalPrice":null,"currency":null,\
                "imageUrl":null,"color":null,"availableSizes":[],"stockText":null}""");

        Optional<ProductPageDetails> result = enricher(properties("key", "http://localhost:1")).extractDetails(response, PRODUCT_URL);

        assertThat(result).isPresent();
        assertThat(result.get().price()).isNull();
        assertThat(result.get().brand()).isEqualTo("Acme");
    }

    @Test
    void extractDetails_withOriginalPriceLowerThanCurrentPrice_discardsOriginalPriceAsSuspicious() {
        JsonNode response = groundedResponse("""
                {"brand":null,"name":null,"price":200,"originalPrice":100,"currency":null,\
                "imageUrl":null,"color":null,"availableSizes":[],"stockText":null}""");

        Optional<ProductPageDetails> result = enricher(properties("key", "http://localhost:1")).extractDetails(response, PRODUCT_URL);

        assertThat(result).isPresent();
        assertThat(result.get().price()).isEqualByComparingTo("200");
        assertThat(result.get().originalPrice()).isNull();
    }

    @Test
    void extractDetails_withInvalidCurrencyCode_discardsCurrency() {
        JsonNode response = groundedResponse("""
                {"brand":null,"name":null,"price":null,"originalPrice":null,"currency":"not-a-currency",\
                "imageUrl":null,"color":null,"availableSizes":[],"stockText":null}""");

        Optional<ProductPageDetails> result = enricher(properties("key", "http://localhost:1")).extractDetails(response, PRODUCT_URL);

        assertThat(result).isEmpty(); // currency was the only field, and it's invalid.
    }

    @Test
    void extractDetails_neverPopulatesImageUrlFromAiResponseRegardlessOfWhatItReturns() {
        JsonNode response = groundedResponse("""
                {"brand":"Acme","name":null,"price":null,"originalPrice":null,"currency":null,\
                "imageUrl":"https://images.nordstrom.com/plausible-looking.jpg","color":null,"availableSizes":[],"stockText":null}""");

        Optional<ProductPageDetails> result = enricher(properties("key", "http://localhost:1")).extractDetails(response, PRODUCT_URL);

        assertThat(result).isPresent();
        assertThat(result.get().imageUrl()).isNull();
    }

    // --- HTTP-layer behavior against a local fake server ---------------------

    @Test
    void enrich_withoutApiKey_returnsEmptyWithoutMakingHttpCall() {
        Optional<ProductPageDetails> result = enricher(properties("", "http://localhost:1")).enrich(PRODUCT_URL);

        assertThat(result).isEmpty();
    }

    @Test
    void enrich_whenFakeServerReturnsErrorStatus_returnsEmptyRatherThanThrowing() throws IOException {
        String baseUrl = startFakeServer(500, "{\"error\": \"boom\"}");

        Optional<ProductPageDetails> result = enricher(properties("key", baseUrl)).enrich(PRODUCT_URL);

        assertThat(result).isEmpty();
    }

    @Test
    void enrich_whenFakeServerReturnsMalformedJson_returnsEmptyRatherThanThrowing() throws IOException {
        String baseUrl = startFakeServer(200, "not valid json {{{");

        Optional<ProductPageDetails> result = enricher(properties("key", baseUrl)).enrich(PRODUCT_URL);

        assertThat(result).isEmpty();
    }

    @Test
    void enrich_whenFakeServerIsSlowerThanReadTimeout_returnsEmptyRatherThanThrowing() throws IOException {
        String baseUrl = startSlowFakeServer(2000);
        RetailSearchProperties shortTimeoutProperties = new RetailSearchProperties(
                "key", "test-model", baseUrl, 200, 200, 25, 4);

        Optional<ProductPageDetails> result = enricher(shortTimeoutProperties).enrich(PRODUCT_URL);

        assertThat(result).isEmpty();
    }

    @Test
    void enrich_whenFakeServerReturnsGroundedValidResponse_returnsDetails() throws IOException {
        String body = groundedResponseJson("""
                {"brand":"Acme","name":"Verified Navy Suit","price":299.99,"originalPrice":null,\
                "currency":"USD","imageUrl":null,"color":null,"availableSizes":[],"stockText":null}""");
        String baseUrl = startFakeServer(200, body);

        Optional<ProductPageDetails> result = enricher(properties("key", baseUrl)).enrich(PRODUCT_URL);

        assertThat(result).isPresent();
        assertThat(result.get().brand()).isEqualTo("Acme");
    }

    // --- Live images are never fetched/enriched at all ------------------------------

    /**
     * Regression test for the removed page-image-fetch fallback: {@code
     * enrich} must issue exactly ONE HTTP request (to the configured OpenAI
     * base URL) and never a second request of any kind, for images or
     * anything else - directly proving "no image enrichment call occurs"
     * and "no Nordstrom product page fetch occurs".
     */
    @Test
    void enrich_makesExactlyOneHttpRequestPerCallNeverASecondRequestForAnImage() throws IOException {
        java.util.concurrent.atomic.AtomicInteger requestCount = new java.util.concurrent.atomic.AtomicInteger();
        String body = groundedResponseJson("""
                {"brand":"Acme","name":"Verified Navy Suit","price":299.99,"originalPrice":null,\
                "currency":"USD","imageUrl":null,"color":null,"availableSizes":[],"stockText":null}""");
        String baseUrl = startFakeServerCountingRequests(200, body, requestCount);

        Optional<ProductPageDetails> result = enricher(properties("key", baseUrl)).enrich(PRODUCT_URL);

        assertThat(result).isPresent();
        assertThat(result.get().imageUrl()).isNull();
        assertThat(requestCount.get()).isEqualTo(1);
    }

    @Test
    void enrich_sendsTheConfiguredModel_neverAHardcodedOrSdkDefault() throws IOException {
        AtomicReference<String> capturedRequestBody = new AtomicReference<>();
        String baseUrl = startFakeServerCapturingRequestBody(200, """
                {"status": "completed", "output": []}
                """, capturedRequestBody);
        RetailSearchProperties properties =
                new RetailSearchProperties("key", "custom-configured-model", baseUrl, 2000, 5000, 25, 4);

        enricher(properties).enrich(PRODUCT_URL);

        JsonNode sentBody = MAPPER.readTree(capturedRequestBody.get());
        assertThat(sentBody.path("model").asString(null)).isEqualTo("custom-configured-model");
    }

    private JsonNode groundedResponse(String outputText) {
        return MAPPER.readTree(groundedResponseJson(outputText));
    }

    private String groundedResponseJson(String outputText) {
        return """
                {
                  "status": "completed",
                  "output": [
                    {
                      "type": "message",
                      "content": [
                        {
                          "type": "output_text",
                          "text": %s,
                          "annotations": [
                            {"type": "url_citation", "url": "%s", "title": "Navy Wedding Suit"}
                          ]
                        }
                      ]
                    }
                  ]
                }
                """.formatted(MAPPER.writeValueAsString(outputText), PRODUCT_URL);
    }

    private JsonNode responseWithCitationAndText(String citationUrl, String outputText) {
        String json = """
                {
                  "status": "completed",
                  "output": [
                    {
                      "type": "message",
                      "content": [
                        {
                          "type": "output_text",
                          "text": %s,
                          "annotations": [
                            {"type": "url_citation", "url": "%s", "title": "Some Other Product"}
                          ]
                        }
                      ]
                    }
                  ]
                }
                """.formatted(MAPPER.writeValueAsString(outputText), citationUrl);
        return MAPPER.readTree(json);
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

    private String startFakeServerCountingRequests(int status, String responseBody, java.util.concurrent.atomic.AtomicInteger requestCount) throws IOException {
        fakeServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        fakeServer.createContext("/", exchange -> {
            requestCount.incrementAndGet();
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
