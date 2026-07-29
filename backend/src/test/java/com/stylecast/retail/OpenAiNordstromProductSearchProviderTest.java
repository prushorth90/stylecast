package com.stylecast.retail;

import com.stylecast.catalog.ProductCategory;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link OpenAiNordstromProductSearchProvider}.
 *
 * <p>Response-normalization behavior ({@link OpenAiNordstromProductSearchProvider#extractCandidates})
 * is tested directly against hand-built JSON fixtures shaped like the OpenAI
 * Responses API. HTTP-layer behavior (timeout, error status, malformed body,
 * missing API key) is tested against a local {@link HttpServer} fake -
 * never the real OpenAI API - per Task 4B's testing requirements.
 */
class OpenAiNordstromProductSearchProviderTest {

    private static final JsonMapper MAPPER = new JsonMapper();

    private final RetailProductSearchRequest request = new RetailProductSearchRequest(
            Retailer.NORDSTROM, ProductCategory.SUIT, List.of("navy", "wedding"), null, null, 10);

    private HttpServer fakeServer;

    @AfterEach
    void stopFakeServer() {
        if (fakeServer != null) {
            fakeServer.stop(0);
        }
    }

    private OpenAiNordstromProductSearchProvider providerWithoutHttp(RetailSearchProperties properties) {
        return new OpenAiNordstromProductSearchProvider(properties, MAPPER, WebClient.builder());
    }

    // --- extractCandidates: pure response-normalization logic ---------------

    @Test
    void extractCandidates_withValidNordstromCitation_normalizesToCandidate() {
        JsonNode response = MAPPER.readTree("""
                {
                  "status": "completed",
                  "output": [
                    {
                      "type": "message",
                      "content": [
                        {
                          "type": "output_text",
                          "annotations": [
                            {
                              "type": "url_citation",
                              "url": "https://www.nordstrom.com/s/navy-wedding-suit/1234567",
                              "title": "Navy Wedding Suit - Nordstrom"
                            }
                          ]
                        }
                      ]
                    }
                  ]
                }
                """);

        List<RetailProductCandidate> candidates =
                providerWithoutHttp(properties("key")).extractCandidates(response, request);

        assertThat(candidates).hasSize(1);
        RetailProductCandidate candidate = candidates.get(0);
        assertThat(candidate.source()).isEqualTo(RetailProductSource.AI_WEB_SEARCH);
        assertThat(candidate.retailer()).isEqualTo(Retailer.NORDSTROM);
        assertThat(candidate.title()).isEqualTo("Navy Wedding Suit - Nordstrom");
        assertThat(candidate.productUrl()).isEqualTo("https://www.nordstrom.com/s/navy-wedding-suit/1234567");
        assertThat(candidate.retrievedAt()).isNotNull();
        // Fields that cannot be independently confirmed from a citation must stay null/empty.
        assertThat(candidate.category()).isNull();
        assertThat(candidate.price()).isNull();
        assertThat(candidate.currency()).isNull();
        assertThat(candidate.imageUrl()).isNull();
        assertThat(candidate.description()).isNull();
        assertThat(candidate.availableSizes()).isEmpty();
        assertThat(candidate.availabilityVerified()).isFalse();
    }

    @Test
    void extractCandidates_filtersOutUnrelatedDomainsAndNonProductNordstromPages() {
        JsonNode response = MAPPER.readTree("""
                {
                  "status": "completed",
                  "output": [
                    {
                      "type": "message",
                      "content": [
                        {
                          "type": "output_text",
                          "annotations": [
                            {"type": "url_citation", "url": "https://www.nordstrom.com/s/navy-suit/111", "title": "Good"},
                            {"type": "url_citation", "url": "https://www.macys.com/s/other/222", "title": "Bad domain"},
                            {"type": "url_citation", "url": "https://www.nordstrom.com/browse/men", "title": "Category page"}
                          ]
                        }
                      ]
                    }
                  ]
                }
                """);

        List<RetailProductCandidate> candidates =
                providerWithoutHttp(properties("key")).extractCandidates(response, request);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).title()).isEqualTo("Good");
    }

    @Test
    void extractCandidates_deduplicatesByCanonicalUrl() {
        JsonNode response = MAPPER.readTree("""
                {
                  "status": "completed",
                  "output": [
                    {
                      "type": "message",
                      "content": [
                        {
                          "type": "output_text",
                          "annotations": [
                            {"type": "url_citation", "url": "https://www.nordstrom.com/s/navy-wedding-suit/1234567?color=navy", "title": "First"},
                            {"type": "url_citation", "url": "https://www.nordstrom.com/s/navy-wedding-suit/1234567", "title": "Second"}
                          ]
                        }
                      ]
                    }
                  ]
                }
                """);

        List<RetailProductCandidate> candidates =
                providerWithoutHttp(properties("key")).extractCandidates(response, request);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).title()).isEqualTo("First");
    }

    @Test
    void extractCandidates_capsResultsAtRequestLimit() {
        JsonNode response = MAPPER.readTree("""
                {
                  "status": "completed",
                  "output": [
                    {
                      "type": "message",
                      "content": [
                        {
                          "type": "output_text",
                          "annotations": [
                            {"type": "url_citation", "url": "https://www.nordstrom.com/s/a/111", "title": "A"},
                            {"type": "url_citation", "url": "https://www.nordstrom.com/s/b/222", "title": "B"},
                            {"type": "url_citation", "url": "https://www.nordstrom.com/s/c/333", "title": "C"}
                          ]
                        }
                      ]
                    }
                  ]
                }
                """);
        RetailProductSearchRequest limitedRequest = new RetailProductSearchRequest(
                Retailer.NORDSTROM, ProductCategory.SUIT, List.of(), null, null, 1);

        List<RetailProductCandidate> candidates =
                providerWithoutHttp(properties("key")).extractCandidates(response, limitedRequest);

        assertThat(candidates).hasSize(1);
    }

    @Test
    void extractCandidates_withNoMatchingCitations_returnsEmptyList() {
        JsonNode response = MAPPER.readTree("""
                {"status": "completed", "output": []}
                """);

        List<RetailProductCandidate> candidates =
                providerWithoutHttp(properties("key")).extractCandidates(response, request);

        assertThat(candidates).isEmpty();
    }

    @Test
    void extractCandidates_withMissingOutputField_returnsEmptyListRatherThanFailing() {
        JsonNode response = MAPPER.readTree("""
                {"status": "completed"}
                """);

        List<RetailProductCandidate> candidates =
                providerWithoutHttp(properties("key")).extractCandidates(response, request);

        assertThat(candidates).isEmpty();
    }

    @Test
    void extractCandidates_withFailedStatus_throwsProviderException() {
        JsonNode response = MAPPER.readTree("""
                {"status": "failed", "error": {"code": "server_error", "message": "boom"}}
                """);

        assertThatThrownBy(() -> providerWithoutHttp(properties("key")).extractCandidates(response, request))
                .isInstanceOf(ProductSearchProviderException.class)
                .hasMessageContaining("boom");
    }

    @Test
    void extractCandidates_withNonObjectTopLevel_throwsProviderException() {
        JsonNode response = MAPPER.readTree("[1, 2, 3]");

        assertThatThrownBy(() -> providerWithoutHttp(properties("key")).extractCandidates(response, request))
                .isInstanceOf(ProductSearchProviderException.class);
    }

    // --- HTTP-layer behavior against a local fake server ---------------------

    @Test
    void search_withoutApiKey_throwsProviderException_withoutMakingHttpCall() {
        OpenAiNordstromProductSearchProvider provider = providerWithoutHttp(properties(""));

        assertThatThrownBy(() -> provider.search(request))
                .isInstanceOf(ProductSearchProviderException.class)
                .hasMessageContaining("OPENAI_API_KEY");
    }

    @Test
    void search_whenFakeServerReturnsValidResponse_normalizesToCandidates() throws IOException {
        String body = """
                {
                  "status": "completed",
                  "output": [
                    {
                      "type": "message",
                      "content": [
                        {
                          "type": "output_text",
                          "annotations": [
                            {"type": "url_citation", "url": "https://www.nordstrom.com/s/navy-wedding-suit/1234567", "title": "Navy Wedding Suit"}
                          ]
                        }
                      ]
                    }
                  ]
                }
                """;
        String baseUrl = startFakeServer(200, body);
        OpenAiNordstromProductSearchProvider provider = providerWithoutHttp(properties("test-key", baseUrl));

        RetailProductSearchResult result = provider.search(request);

        assertThat(result.candidates()).hasSize(1);
        assertThat(result.candidates().get(0).productUrl())
                .isEqualTo("https://www.nordstrom.com/s/navy-wedding-suit/1234567");
    }

    @Test
    void search_whenFakeServerReturnsErrorStatus_throwsProviderException() throws IOException {
        String baseUrl = startFakeServer(500, "{\"error\": {\"message\": \"internal error\"}}");
        OpenAiNordstromProductSearchProvider provider = providerWithoutHttp(properties("test-key", baseUrl));

        assertThatThrownBy(() -> provider.search(request))
                .isInstanceOf(ProductSearchProviderException.class);
    }

    @Test
    void search_whenFakeServerReturnsMalformedJson_throwsProviderException() throws IOException {
        String baseUrl = startFakeServer(200, "not valid json {{{");
        OpenAiNordstromProductSearchProvider provider = providerWithoutHttp(properties("test-key", baseUrl));

        assertThatThrownBy(() -> provider.search(request))
                .isInstanceOf(ProductSearchProviderException.class);
    }

    @Test
    void search_whenFakeServerIsSlowerThanReadTimeout_throwsProviderException() throws IOException {
        String baseUrl = startSlowFakeServer(2000);
        // Read timeout much shorter than the server's artificial delay above.
        RetailSearchProperties shortTimeoutProperties = new RetailSearchProperties(
                "test-key", "gpt-4.1", baseUrl, 200, 200, 25);
        OpenAiNordstromProductSearchProvider provider = providerWithoutHttp(shortTimeoutProperties);

        assertThatThrownBy(() -> provider.search(request))
                .isInstanceOf(ProductSearchProviderException.class);
    }

    private RetailSearchProperties properties(String apiKey) {
        return properties(apiKey, "http://localhost:1");
    }

    private RetailSearchProperties properties(String apiKey, String baseUrl) {
        return new RetailSearchProperties(apiKey, "gpt-4.1", baseUrl, 2000, 5000, 25);
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
