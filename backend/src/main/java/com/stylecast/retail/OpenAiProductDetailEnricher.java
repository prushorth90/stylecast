package com.stylecast.retail;

import io.netty.channel.ChannelOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * {@link ProductDetailEnricher} backed by a second, narrowly-scoped OpenAI
 * Responses API call per candidate URL - reusing the same {@code
 * web_search} tool restricted to {@code nordstrom.com} that {@link
 * OpenAiNordstromProductSearchProvider} already uses, rather than StyleCast
 * fetching/parsing the Nordstrom page itself for brand/name/price/color/
 * sizes/stock/department (see the retail boundaries in {@code
 * .github/copilot-instructions.md}: "do not scrape Nordstrom").
 *
 * <p>Trust model: a claimed field is only kept if (a) the response's own
 * {@code url_citation} annotations include the exact requested product URL
 * (proving the model actually grounded its answer in that page, the same
 * mechanical signal {@link OpenAiNordstromProductSearchProvider} already
 * relies on) and (b) the field passes basic sanity validation (non-blank,
 * plausible price range, 3-letter currency code, etc.). Anything else is
 * discarded rather than trusted - see {@link #extractDetails}.
 *
 * <p><b>Product images are deliberately never populated for live
 * Nordstrom candidates</b> (a live, authorized product feed is not yet
 * available - see docs/ROADMAP.md): {@link ProductPageDetails#imageUrl()}
 * is always {@code null} here, regardless of anything the model's JSON
 * response happens to include under an {@code imageUrl} key - this class
 * makes exactly ONE outbound HTTP request per candidate (to the
 * configured OpenAI base URL) and never makes a second request, of any
 * kind, solely to obtain an image. {@code imageUrl} remains a field on
 * {@link ProductPageDetails}/{@link RetailProductCandidate}/the persisted
 * entity/DTO purely for backward compatibility (no schema migration is
 * warranted just to drop it) - new live items simply never set it.
 *
 * <p>Never throws: {@link #enrich} catches every failure (missing API key,
 * timeout, error response, malformed/ungrounded output) and never lets an
 * exception escape, so a failed enrichment attempt never discards an
 * otherwise-valid candidate.
 */
@Component
public class OpenAiProductDetailEnricher implements ProductDetailEnricher {

    private static final Logger log = LoggerFactory.getLogger(OpenAiProductDetailEnricher.class);

    private static final String NORDSTROM_DOMAIN = "nordstrom.com";
    private static final BigDecimal MAX_SANE_PRICE = BigDecimal.valueOf(50_000);

    private final RetailSearchProperties properties;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    public OpenAiProductDetailEnricher(
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
    public Optional<ProductPageDetails> enrich(String productUrl) {
        if (!properties.hasApiKey()) {
            return Optional.empty();
        }
        try {
            JsonNode responseJson = callOpenAi(buildRequestBody(productUrl));
            return extractDetails(responseJson, productUrl);
        } catch (RuntimeException e) {
            // Covers timeouts, non-2xx responses, malformed JSON, and any other
            // unexpected failure - enrichment failing must never discard the candidate.
            log.debug("Product detail enrichment failed for a candidate URL: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }


    private ObjectNode buildRequestBody(String productUrl) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", properties.openaiModel());
        root.put("input", buildPrompt(productUrl));
        root.put("tool_choice", "required");

        ArrayNode tools = root.putArray("tools");
        ObjectNode webSearchTool = tools.addObject();
        webSearchTool.put("type", "web_search");
        webSearchTool.put("search_context_size", "low");
        ObjectNode filters = webSearchTool.putObject("filters");
        filters.putArray("allowed_domains").add(NORDSTROM_DOMAIN);

        return root;
    }

    private String buildPrompt(String productUrl) {
        return "Use web search to open this exact Nordstrom product page and report only what is explicitly "
                + "shown on it: " + productUrl + ". "
                + "Respond with ONLY a single-line JSON object (no other text before or after it) with exactly "
                + "these keys: brand, name, price, originalPrice, currency, color, availableSizes, "
                + "stockText, department. Use null for any field not explicitly shown on the page - never guess, "
                + "estimate, or recall a value from memory or training data. price and originalPrice must be plain "
                + "numbers with no currency symbol, or null. availableSizes must be a JSON array of strings "
                + "(an empty array if none are shown). stockText must be the literal short stock/availability "
                + "text shown on the page (for example \"In stock\"), or null if none is shown. department must "
                + "be exactly one of \"men\", \"women\", \"unisex\", or null - based only on an explicit "
                + "department/breadcrumb/category label (for example a breadcrumb like \"Men > Clothing\") or "
                + "explicit gender wording shown on the page (for example a \"Women's\" label); never infer it "
                + "from a product image alone, and use null if the page does not clearly indicate one.";
    }

    private JsonNode callOpenAi(ObjectNode requestBody) {
        String responseBody = webClient.post()
                .uri("/responses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.openaiApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofMillis(properties.connectTimeoutMs() + properties.readTimeoutMs()));

        if (responseBody == null) {
            throw new IllegalStateException("Product detail enrichment provider returned an empty response");
        }
        return objectMapper.readTree(responseBody);
    }

    // Package-private (rather than private) so OpenAiProductDetailEnricherTest can exercise
    // response normalization directly against hand-built JsonNode fixtures, without needing
    // a real or fake HTTP call for every case.
    Optional<ProductPageDetails> extractDetails(JsonNode responseJson, String requestedProductUrl) {
        if (responseJson == null || !responseJson.isObject()) {
            return Optional.empty();
        }
        if ("failed".equals(responseJson.path("status").asString(null))) {
            return Optional.empty();
        }

        JsonNode output = responseJson.path("output");
        if (!output.isArray()) {
            return Optional.empty();
        }

        String canonicalRequested = NordstromUrlValidator.isValidNordstromProductUrl(requestedProductUrl)
                ? NordstromUrlValidator.canonicalize(requestedProductUrl)
                : requestedProductUrl;

        boolean groundedToRequestedUrl = false;
        StringBuilder text = new StringBuilder();
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
                text.append(contentItem.path("text").asString(""));

                JsonNode annotations = contentItem.path("annotations");
                if (!annotations.isArray()) {
                    continue;
                }
                for (JsonNode annotation : annotations) {
                    if (!"url_citation".equals(annotation.path("type").asString(null))) {
                        continue;
                    }
                    String url = annotation.path("url").asString(null);
                    if (url != null && NordstromUrlValidator.isValidNordstromProductUrl(url)
                            && NordstromUrlValidator.canonicalize(url).equals(canonicalRequested)) {
                        groundedToRequestedUrl = true;
                    }
                }
            }
        }

        // The tool must have actually cited the exact page we asked about - otherwise
        // whatever the model claims cannot be trusted as page-verified.
        if (!groundedToRequestedUrl) {
            return Optional.empty();
        }

        JsonNode json = parseJsonObject(text.toString());
        if (json == null) {
            return Optional.empty();
        }

        BigDecimal price = validPrice(json.path("price"));
        // imageUrl is deliberately never populated from the AI response here (see class docs) -
        // live Nordstrom candidates never depend on an image; the field stays null.
        ProductPageDetails details = new ProductPageDetails(
                validString(json.path("brand"), 150),
                validString(json.path("name"), 300),
                price,
                validOriginalPrice(json.path("originalPrice"), price),
                validCurrency(json.path("currency")),
                null,
                validString(json.path("color"), 50),
                validSizes(json.path("availableSizes")),
                validString(json.path("stockText"), 100),
                validAudience(json.path("department")));

        return isEmpty(details) ? Optional.empty() : Optional.of(details);
    }

    private boolean isEmpty(ProductPageDetails details) {
        return details.brand() == null && details.name() == null && details.price() == null
                && details.originalPrice() == null && details.currency() == null && details.imageUrl() == null
                && details.color() == null && details.availableSizes().isEmpty() && details.stockText() == null
                && details.audience() == null;
    }

    private JsonNode parseJsonObject(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(trimmed.substring(start, end + 1));
            return node.isObject() ? node : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String validString(JsonNode node, int maxLength) {
        if (node == null || !node.isString()) {
            return null;
        }
        String value = node.asString("").trim();
        if (value.isEmpty() || value.length() > maxLength || "null".equalsIgnoreCase(value)) {
            return null;
        }
        return value;
    }

    private BigDecimal validPrice(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        BigDecimal value;
        try {
            if (node.isNumber()) {
                value = node.decimalValue();
            } else if (node.isString()) {
                value = new BigDecimal(node.asString().trim());
            } else {
                return null;
            }
        } catch (RuntimeException e) {
            return null;
        }
        if (value.compareTo(BigDecimal.ZERO) <= 0 || value.compareTo(MAX_SANE_PRICE) > 0) {
            return null;
        }
        return value;
    }

    /** Only trusted when it parses as a sane price AND is not lower than the current price (else suspicious). */
    private BigDecimal validOriginalPrice(JsonNode node, BigDecimal currentPrice) {
        BigDecimal value = validPrice(node);
        if (value == null) {
            return null;
        }
        if (currentPrice != null && value.compareTo(currentPrice) < 0) {
            return null;
        }
        return value;
    }

    private String validCurrency(JsonNode node) {
        String value = validString(node, 10);
        if (value == null) {
            return null;
        }
        String upper = value.toUpperCase(Locale.ROOT);
        return upper.matches("[A-Z]{3}") ? upper : null;
    }

    private List<String> validSizes(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> sizes = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isString()) {
                continue;
            }
            String value = item.asString("").trim();
            if (!value.isEmpty() && value.length() <= 20 && !sizes.contains(value)) {
                sizes.add(value);
            }
        }
        return List.copyOf(sizes);
    }

    /**
     * Maps the model's reported {@code department} string to a {@link
     * CandidateAudience}, or {@code null} when it isn't one of the exact
     * expected values (never guessed).
     */
    private CandidateAudience validAudience(JsonNode node) {
        String value = validString(node, 20);
        if (value == null) {
            return null;
        }
        return switch (value.toLowerCase(java.util.Locale.ROOT)) {
            case "men", "man", "male" -> CandidateAudience.MEN;
            case "women", "woman", "female" -> CandidateAudience.WOMEN;
            case "unisex", "gender-neutral", "gender neutral" -> CandidateAudience.UNISEX;
            default -> null;
        };
    }
}
