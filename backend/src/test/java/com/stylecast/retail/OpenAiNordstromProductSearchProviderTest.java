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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * never the real OpenAI API - per Task 4B's testing requirements. Product-
 * detail enrichment is tested against a {@link FakeProductDetailEnricher} -
 * never a real {@link OpenAiProductDetailEnricher} call.
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
        return providerWithoutHttp(properties, new FakeProductDetailEnricher());
    }

    private OpenAiNordstromProductSearchProvider providerWithoutHttp(RetailSearchProperties properties, ProductDetailEnricher enricher) {
        return new OpenAiNordstromProductSearchProvider(properties, MAPPER, WebClient.builder(), enricher);
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
        // category is carried forward from the request itself (already known, never invented).
        assertThat(candidate.category()).isEqualTo(ProductCategory.SUIT);
        // Fields that cannot be independently confirmed from a citation must stay null/empty.
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
    void extractCandidates_setsCategoryFromTheRequestItself_neverInventingIt() {
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
                            {"type": "url_citation", "url": "https://www.nordstrom.com/s/navy-suit/111", "title": "Navy Suit"}
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
        assertThat(candidates.get(0).category()).isEqualTo(ProductCategory.SUIT);
    }

    @Test
    void extractCandidates_forMensRequest_rejectsExplicitlyWomensMarkedTitles() {
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
                            {"type": "url_citation", "url": "https://www.nordstrom.com/s/mens-trousers/111", "title": "Classic Trouser (Men)"},
                            {"type": "url_citation", "url": "https://www.nordstrom.com/s/womens-blouse/222", "title": "Silk Blouse (Women)"},
                            {"type": "url_citation", "url": "https://www.nordstrom.com/s/ballet-flat/333", "title": "Ballet Flat (Women)"},
                            {"type": "url_citation", "url": "https://www.nordstrom.com/s/leather-belt/444", "title": "Leather Belt"}
                          ]
                        }
                      ]
                    }
                  ]
                }
                """);
        RetailProductSearchRequest mensRequest = new RetailProductSearchRequest(
                Retailer.NORDSTROM, ProductCategory.TROUSERS, List.of(), null, null, TargetAudience.MEN, 10);

        List<RetailProductCandidate> candidates =
                providerWithoutHttp(properties("key")).extractCandidates(response, mensRequest);

        // The men's-marked trouser and the gender-neutral belt are kept; both women's-marked
        // items (blouse and ballet flats - the exact reported bug scenario) are rejected.
        assertThat(candidates).extracting(RetailProductCandidate::title)
                .containsExactlyInAnyOrder("Classic Trouser (Men)", "Leather Belt");
    }

    @Test
    void extractCandidates_forWomensRequest_rejectsExplicitlyMensMarkedTitles() {
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
                            {"type": "url_citation", "url": "https://www.nordstrom.com/s/womens-dress/111", "title": "Floral Dress (Women)"},
                            {"type": "url_citation", "url": "https://www.nordstrom.com/s/mens-tie/222", "title": "Silk Tie (Men)"},
                            {"type": "url_citation", "url": "https://www.nordstrom.com/s/mens-dress-shoes/333", "title": "Derby Dress Shoes (Men)"}
                          ]
                        }
                      ]
                    }
                  ]
                }
                """);
        RetailProductSearchRequest womensRequest = new RetailProductSearchRequest(
                Retailer.NORDSTROM, ProductCategory.DRESS, List.of(), null, null, TargetAudience.WOMEN, 10);

        List<RetailProductCandidate> candidates =
                providerWithoutHttp(properties("key")).extractCandidates(response, womensRequest);

        // The women's-marked dress is kept; both men's-marked items (tie and dress shoes -
        // the exact reported bug scenario) are rejected.
        assertThat(candidates).extracting(RetailProductCandidate::title).containsExactly("Floral Dress (Women)");
    }

    @Test
    void extractCandidates_forUnisexRequest_appliesNoAudienceFiltering() {
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
                            {"type": "url_citation", "url": "https://www.nordstrom.com/s/mens-tie/111", "title": "Silk Tie (Men)"},
                            {"type": "url_citation", "url": "https://www.nordstrom.com/s/womens-scarf/222", "title": "Wool Scarf (Women)"}
                          ]
                        }
                      ]
                    }
                  ]
                }
                """);
        RetailProductSearchRequest unisexRequest = new RetailProductSearchRequest(
                Retailer.NORDSTROM, ProductCategory.ACCESSORY, List.of(), null, null, TargetAudience.UNISEX, 10);

        List<RetailProductCandidate> candidates =
                providerWithoutHttp(properties("key")).extractCandidates(response, unisexRequest);

        // UNISEX prefers gender-neutral results but never hard-rejects on department grounds.
        assertThat(candidates).hasSize(2);
    }

    @Test
    void extractCandidates_forNoPreferenceRequest_appliesNoAudienceFiltering() {
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
                            {"type": "url_citation", "url": "https://www.nordstrom.com/s/mens-tie/111", "title": "Silk Tie (Men)"},
                            {"type": "url_citation", "url": "https://www.nordstrom.com/s/womens-scarf/222", "title": "Wool Scarf (Women)"}
                          ]
                        }
                      ]
                    }
                  ]
                }
                """);

        // `request` defaults targetAudience to NO_PREFERENCE via the legacy constructor.
        List<RetailProductCandidate> candidates =
                providerWithoutHttp(properties("key")).extractCandidates(response, request);

        assertThat(candidates).hasSize(2);
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

    // --- enrichCandidates: bounded, failure-tolerant product-detail enrichment ---

    @Test
    void enrichCandidates_mergesConfirmedDetailsIntoTheCandidate() {
        RetailProductCandidate base = plainCandidate("https://www.nordstrom.com/s/suit/111");
        ProductPageDetails details = new ProductPageDetails(
                "Acme", "Verified Navy Suit", new java.math.BigDecimal("299.99"), null, "USD",
                "https://images.nordstrom.com/suit.jpg", "Navy", List.of("40R", "42R"), "In stock", null);
        FakeProductDetailEnricher enricher = new FakeProductDetailEnricher();
        enricher.results.put(base.productUrl(), details);

        List<RetailProductCandidate> result =
                providerWithoutHttp(properties("key"), enricher).enrichCandidates(List.of(base), TargetAudience.NO_PREFERENCE);

        assertThat(result).hasSize(1);
        RetailProductCandidate enriched = result.get(0);
        assertThat(enriched.title()).isEqualTo("Verified Navy Suit");
        assertThat(enriched.brand()).isEqualTo("Acme");
        assertThat(enriched.price()).isEqualByComparingTo("299.99");
        assertThat(enriched.priceVerified()).isTrue();
        assertThat(enriched.availableSizes()).containsExactly("40R", "42R");
        assertThat(enriched.sizeVerified()).isTrue();
        assertThat(enriched.availabilityVerified()).isTrue();
        // Unaffected fields are untouched.
        assertThat(enriched.productUrl()).isEqualTo(base.productUrl());
        assertThat(enriched.source()).isEqualTo(base.source());
    }

    @Test
    void enrichCandidates_whenEnricherFindsNothing_keepsCandidateUnverified() {
        RetailProductCandidate base = plainCandidate("https://www.nordstrom.com/s/suit/111");
        FakeProductDetailEnricher enricher = new FakeProductDetailEnricher(); // no results configured

        List<RetailProductCandidate> result =
                providerWithoutHttp(properties("key"), enricher).enrichCandidates(List.of(base), TargetAudience.NO_PREFERENCE);

        assertThat(result).containsExactly(base);
        assertThat(result.get(0).priceVerified()).isFalse();
        assertThat(result.get(0).sizeVerified()).isFalse();
        assertThat(result.get(0).availabilityVerified()).isFalse();
    }

    @Test
    void enrichCandidates_whenEnricherThrows_stillKeepsTheOriginalCandidate() {
        RetailProductCandidate base = plainCandidate("https://www.nordstrom.com/s/suit/111");
        ProductDetailEnricher throwingEnricher = url -> {
            throw new RuntimeException("boom");
        };

        List<RetailProductCandidate> result =
                providerWithoutHttp(properties("key"), throwingEnricher).enrichCandidates(List.of(base), TargetAudience.NO_PREFERENCE);

        assertThat(result).containsExactly(base);
    }

    @Test
    void enrichCandidates_whenEnrichmentRevealsConflictingDepartment_dropsTheCandidate() {
        RetailProductCandidate base = plainCandidate("https://www.nordstrom.com/s/ambiguous-item/111");
        ProductPageDetails details = new ProductPageDetails(
                null, null, null, null, null, null, null, List.of(), null, CandidateAudience.WOMEN);
        FakeProductDetailEnricher enricher = new FakeProductDetailEnricher();
        enricher.results.put(base.productUrl(), details);

        List<RetailProductCandidate> result =
                providerWithoutHttp(properties("key"), enricher).enrichCandidates(List.of(base), TargetAudience.MEN);

        // Enrichment independently confirmed this is a women's product - a men's request must
        // reject it even though its title alone carried no marker.
        assertThat(result).isEmpty();
    }

    @Test
    void enrichCandidates_respectsTheConfiguredMaxCandidatesBound() {
        RetailProductCandidate first = plainCandidate("https://www.nordstrom.com/s/suit-1/111");
        RetailProductCandidate second = plainCandidate("https://www.nordstrom.com/s/suit-2/222");
        FakeProductDetailEnricher enricher = new FakeProductDetailEnricher();
        ProductPageDetails details = new ProductPageDetails(
                "Acme", "Verified", new java.math.BigDecimal("100.00"), null, "USD", null, null, List.of(), null, null);
        enricher.results.put(first.productUrl(), details);
        enricher.results.put(second.productUrl(), details);
        RetailSearchProperties boundToOne = new RetailSearchProperties("key", "gpt-4.1", "http://localhost:1", 2000, 5000, 25, 1);

        List<RetailProductCandidate> result =
                providerWithoutHttp(boundToOne, enricher).enrichCandidates(List.of(first, second), TargetAudience.NO_PREFERENCE);

        assertThat(result.get(0).priceVerified()).isTrue();
        // Bounded to 1 enrichment attempt - the second candidate is left unenriched
        // even though the fake enricher has data for it.
        assertThat(result.get(1).priceVerified()).isFalse();
    }

    private RetailProductCandidate plainCandidate(String url) {
        return new RetailProductCandidate(
                RetailProductSource.AI_WEB_SEARCH, Retailer.NORDSTROM, "Some Suit", null, ProductCategory.SUIT,
                null, null, null, url, null, null, null, List.of(), null, false, false, false,
                CandidateAudience.UNKNOWN, java.time.Instant.now(), "OpenAI web_search url_citation");
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
                "test-key", "gpt-4.1", baseUrl, 200, 200, 25, 4);
        OpenAiNordstromProductSearchProvider provider = providerWithoutHttp(shortTimeoutProperties);

        assertThatThrownBy(() -> provider.search(request))
                .isInstanceOf(ProductSearchProviderException.class);
    }

    private RetailSearchProperties properties(String apiKey) {
        return properties(apiKey, "http://localhost:1");
    }

    private RetailSearchProperties properties(String apiKey, String baseUrl) {
        return new RetailSearchProperties(apiKey, "gpt-4.1", baseUrl, 2000, 5000, 25, 4);
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

    /** Fake {@link ProductDetailEnricher} - returns configured details per URL, or empty by default. */
    private static final class FakeProductDetailEnricher implements ProductDetailEnricher {
        private final Map<String, ProductPageDetails> results = new HashMap<>();

        @Override
        public Optional<ProductPageDetails> enrich(String productUrl) {
            return Optional.ofNullable(results.get(productUrl));
        }
    }
}
