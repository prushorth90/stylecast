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

/**
 * {@link RetailProductSearchProvider} backed by the OpenAI Responses API's
 * built-in {@code web_search} tool (see
 * <a href="https://developers.openai.com/api/docs/guides/tools-web-search">OpenAI web search docs</a>
 * and {@code POST /v1/responses}), restricted to {@code nordstrom.com} via
 * the tool's {@code filters.allowed_domains}.
 *
 * <p>Only {@code url_citation} annotations from the model's response - which
 * are populated by the search tool itself, not generated free-form by the
 * model - are used as candidate sources. Every other candidate field (price,
 * currency, image, description, sizes, availability) is left {@code null}/
 * empty because it cannot be independently confirmed from a citation alone;
 * this is a deliberate design choice to satisfy the "never invent a field"
 * requirement rather than parsing model prose.
 */
@Component
public class OpenAiNordstromProductSearchProvider implements RetailProductSearchProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiNordstromProductSearchProvider.class);

    private static final String NORDSTROM_DOMAIN = "nordstrom.com";

    private final RetailSearchProperties properties;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    public OpenAiNordstromProductSearchProvider(
            RetailSearchProperties properties,
            ObjectMapper objectMapper,
            WebClient.Builder webClientBuilder) {
        this.properties = properties;
        this.objectMapper = objectMapper;

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
        return new RetailProductSearchResult(candidates);
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
            String canonicalUrl = NordstromUrlValidator.canonicalize(citation.url());
            if (byCanonicalUrl.containsKey(canonicalUrl)) {
                continue;
            }
            byCanonicalUrl.put(canonicalUrl, new RetailProductCandidate(
                    RetailProductSource.AI_WEB_SEARCH,
                    request.retailer(),
                    citation.title(),
                    null,
                    null,
                    null,
                    canonicalUrl,
                    null,
                    null,
                    List.of(),
                    false,
                    retrievedAt,
                    "OpenAI web_search url_citation"));
        }

        return byCanonicalUrl.values().stream().limit(request.limit()).toList();
    }

    private record RawCitation(String url, String title) {
    }
}
