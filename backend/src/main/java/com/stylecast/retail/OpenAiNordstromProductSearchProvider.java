package com.stylecast.retail;

import com.stylecast.catalog.ProductCategory;
import io.netty.channel.ChannelOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@link RetailProductSearchProvider} backed by the OpenAI Responses API's
 * built-in {@code web_search} tool (see
 * <a href="https://developers.openai.com/api/docs/guides/tools-web-search">OpenAI web search docs</a>
 * and {@code POST /v1/responses}), restricted to {@code nordstrom.com} via
 * the tool's {@code filters.allowed_domains}.
 *
 * <p>Only {@code url_citation} annotations from the model's response - which
 * are populated by the search tool itself, not generated free-form by the
 * model - are used as candidate sources. {@code category} is set to the
 * request's own (already-known) category - never invented, just carried
 * forward. Every other candidate field (price, currency, image,
 * description, brand, color, sizes, availability) starts {@code null}/empty
 * because it cannot be independently confirmed from a citation alone; a
 * bounded number of candidates are then passed to a {@link
 * ProductDetailEnricher} (see {@link #enrichCandidates}) which may fill
 * some of them in with independently confirmed data.
 *
 * <p>Candidates whose title carries an explicit, conflicting men's/women's
 * marker for the request's {@link TargetAudience} are filtered out (see
 * {@link CandidateAudienceClassifier}) - this is what prevents e.g. a
 * men's-only request from returning a women's blouse alongside men's
 * trousers. The same acceptability check is re-applied after enrichment,
 * since enrichment may independently confirm a department that conflicts
 * with the request even when the title alone did not.
 */
@Component
public class OpenAiNordstromProductSearchProvider implements RetailProductSearchProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiNordstromProductSearchProvider.class);

    private static final String NORDSTROM_DOMAIN = "nordstrom.com";

    private final RetailSearchProperties properties;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;
    private final ProductDetailEnricher enricher;

    public OpenAiNordstromProductSearchProvider(
            RetailSearchProperties properties,
            ObjectMapper objectMapper,
            WebClient.Builder webClientBuilder,
            ProductDetailEnricher enricher) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.enricher = enricher;

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(properties.connectTimeoutMs()))
                .responseTimeout(Duration.ofMillis(properties.readTimeoutMs()));

        this.webClient = webClientBuilder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .baseUrl(properties.baseUrl())
                .build();
    }

    @Override
    public RetailProductSearchResult search(RetailProductSearchRequest request) {
        if (!properties.hasApiKey()) {
            throw new ProductSearchProviderException(
                    "Retail product search is not configured: OPENAI_API_KEY is not set");
        }

        ObjectNode requestBody = buildRequestBody(request);
        JsonNode responseJson = callOpenAi(requestBody);
        List<RetailProductCandidate> candidates = extractCandidates(responseJson, request);
        List<RetailProductCandidate> enriched = enrichCandidates(candidates, request.targetAudience());
        return new RetailProductSearchResult(enriched);
    }

    /**
     * Attempts to enrich up to {@link RetailSearchProperties#enrichmentMaxCandidates()}
     * candidates (in list order) with {@link #enricher}; any candidate beyond
     * that bound, or whose enrichment attempt fails or finds nothing, is
     * returned unchanged - enrichment is strictly additive and never removes
     * or invalidates an otherwise-valid candidate on its own. However, a
     * candidate whose enriched {@code audience} is no longer acceptable for
     * {@code requestedDepartment} (see {@link CandidateAudienceClassifier#isAcceptable})
     * is dropped here - enrichment can reveal a more trustworthy, conflicting
     * department signal (breadcrumb/taxonomy) than the title alone showed.
     */
    List<RetailProductCandidate> enrichCandidates(
            List<RetailProductCandidate> candidates, TargetAudience requestedDepartment) {
        List<RetailProductCandidate> result = new ArrayList<>(candidates.size());
        int attempted = 0;
        for (RetailProductCandidate candidate : candidates) {
            RetailProductCandidate enriched = candidate;
            if (attempted < properties.enrichmentMaxCandidates()) {
                attempted++;
                enriched = tryEnrich(candidate);
            }
            if (CandidateAudienceClassifier.isAcceptable(enriched.audience(), requestedDepartment)) {
                result.add(enriched);
            }
        }
        return result;
    }

    /**
     * {@link ProductDetailEnricher} implementations must never throw, but this
     * is a defensive safety net regardless - a misbehaving enricher must still
     * never discard an otherwise-valid candidate.
     */
    private RetailProductCandidate tryEnrich(RetailProductCandidate candidate) {
        try {
            Optional<ProductPageDetails> details = enricher.enrich(candidate.productUrl());
            return details.map(candidate::withPageDetails).orElse(candidate);
        } catch (RuntimeException e) {
            log.debug("Product detail enrichment failed for a candidate URL: {}", e.getClass().getSimpleName());
            return candidate;
        }
    }

    private ObjectNode buildRequestBody(RetailProductSearchRequest request) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", properties.openaiModel());
        root.put("input", buildSearchPrompt(request));
        root.put("tool_choice", "required");

        ArrayNode tools = root.putArray("tools");
        ObjectNode webSearchTool = tools.addObject();
        webSearchTool.put("type", "web_search");
        webSearchTool.put("search_context_size", "low");
        ObjectNode filters = webSearchTool.putObject("filters");
        filters.putArray("allowed_domains").add(NORDSTROM_DOMAIN);

        return root;
    }

    private String buildSearchPrompt(RetailProductSearchRequest request) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Search nordstrom.com only and find up to ")
                .append(request.limit())
                .append(" real, currently live nordstrom.com product page URLs matching this request. ");

        ProductCategory category = request.category();
        if (category != null) {
            prompt.append("Category: ").append(category.name()).append(". ");
        }
        if (!request.keywords().isEmpty()) {
            prompt.append("Keywords: ").append(String.join(", ", request.keywords())).append(". ");
        }
        if (request.maxPrice() != null) {
            prompt.append("Maximum price: ").append(request.maxPrice()).append(" USD. ");
        }
        if (request.clothingSize() != null && !request.clothingSize().isBlank()) {
            prompt.append("Clothing size: ").append(request.clothingSize()).append(". ");
        }
        prompt.append("Only report products you actually found via web search on nordstrom.com. ")
                .append("Do not invent, guess, or recall products from memory or training data. ")
                .append("If no matching products are found, say so plainly.");
        return prompt.toString();
    }

    private JsonNode callOpenAi(ObjectNode requestBody) {
        String responseBody;
        try {
            responseBody = webClient.post()
                    .uri("/responses")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.openaiApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofMillis(properties.connectTimeoutMs() + properties.readTimeoutMs()));
        } catch (WebClientResponseException e) {
            // Never log the API key or the full request/response body - only status.
            log.warn("Retail product search provider returned HTTP {}", e.getStatusCode());
            throw new ProductSearchProviderException(
                    "Nordstrom product search provider returned an error: HTTP " + e.getStatusCode(), e);
        } catch (WebClientRequestException e) {
            log.warn("Retail product search provider request failed: {}", e.getClass().getSimpleName());
            throw new ProductSearchProviderException("Nordstrom product search provider request failed", e);
        } catch (RuntimeException e) {
            // Covers timeouts (Mono#block(Duration) throws IllegalStateException when the
            // deadline elapses) and any other unexpected failure invoking the provider.
            log.warn("Retail product search provider call failed: {}", e.getClass().getSimpleName());
            throw new ProductSearchProviderException("Nordstrom product search provider call failed", e);
        }

        if (responseBody == null) {
            throw new ProductSearchProviderException("Nordstrom product search provider returned an empty response");
        }

        try {
            return objectMapper.readTree(responseBody);
        } catch (JacksonException e) {
            throw new ProductSearchProviderException("Nordstrom product search provider returned malformed JSON", e);
        }
    }

    // Package-private (rather than private) so OpenAiNordstromProductSearchProviderTest
    // can exercise response normalization directly against hand-built JsonNode fixtures,
    // without needing a real or fake HTTP call for every case.
    List<RetailProductCandidate> extractCandidates(JsonNode responseJson, RetailProductSearchRequest request) {
        if (responseJson == null || !responseJson.isObject()) {
            throw new ProductSearchProviderException("Nordstrom product search provider returned an unexpected response shape");
        }

        String status = responseJson.path("status").asString(null);
        if ("failed".equals(status)) {
            String message = responseJson.path("error").path("message").asString("unknown provider error");
            throw new ProductSearchProviderException("Nordstrom product search provider reported failure: " + message);
        }

        JsonNode output = responseJson.path("output");
        if (!output.isArray()) {
            return List.of();
        }

        List<RawCitation> citations = new ArrayList<>();
        for (JsonNode item : output) {
            if (!"message".equals(item.path("type").asString(null))) {
                continue;
            }
            JsonNode content = item.path("content");
            if (!content.isArray()) {
                continue;
            }
            for (JsonNode contentItem : content) {
                if (!"output_text".equals(contentItem.path("type").asString(null))) {
                    continue;
                }
                JsonNode annotations = contentItem.path("annotations");
                if (!annotations.isArray()) {
                    continue;
                }
                for (JsonNode annotation : annotations) {
                    if (!"url_citation".equals(annotation.path("type").asString(null))) {
                        continue;
                    }
                    String url = annotation.path("url").asString(null);
                    String title = annotation.path("title").asString(null);
                    if (url != null) {
                        citations.add(new RawCitation(url, title));
                    }
                }
            }
        }

        Instant retrievedAt = Instant.now();
        Map<String, RetailProductCandidate> byCanonicalUrl = new LinkedHashMap<>();
        for (RawCitation citation : citations) {
            if (!NordstromUrlValidator.isValidNordstromProductUrl(citation.url())) {
                continue;
            }
            CandidateAudience candidateAudience = CandidateAudienceClassifier.classifyFromTitle(citation.title());
            if (!CandidateAudienceClassifier.isAcceptable(candidateAudience, request.targetAudience())) {
                continue;
            }
            String canonicalUrl = NordstromUrlValidator.canonicalize(citation.url());
            if (byCanonicalUrl.containsKey(canonicalUrl)) {
                continue;
            }
            byCanonicalUrl.put(canonicalUrl, new RetailProductCandidate(
                    RetailProductSource.AI_WEB_SEARCH,
                    request.retailer(),
                    citation.title(),
                    null,
                    request.category(),
                    null,
                    null,
                    null,
                    canonicalUrl,
                    null,
                    null,
                    null,
                    List.of(),
                    null,
                    false,
                    false,
                    false,
                    candidateAudience,
                    retrievedAt,
                    "OpenAI web_search url_citation"));
        }

        return byCanonicalUrl.values().stream().limit(request.limit()).toList();
    }

    private record RawCitation(String url, String title) {
    }
}
