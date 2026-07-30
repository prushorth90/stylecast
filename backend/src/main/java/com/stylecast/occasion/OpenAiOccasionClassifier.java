package com.stylecast.occasion;

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

/**
 * {@link OccasionClassifier} backed by the OpenAI Responses API's
 * {@code text.format = json_schema} structured-output mechanism (see
 * <a href="https://developers.openai.com/api/docs/guides/structured-outputs">
 * OpenAI structured outputs docs</a> and {@code POST /v1/responses}), which
 * guarantees the model's JSON response matches {@link #buildJsonSchema()}
 * exactly - every field present, every enum-valued field restricted to a
 * known member. The response is still re-checked by
 * {@link OccasionInterpretationValidator} before use (schema conformance
 * alone doesn't guarantee numeric ranges like formality 1-10).
 *
 * <p>Never persists or returns anything on failure: a missing API key, HTTP
 * error, timeout, malformed JSON, or failed validation all throw
 * {@link OccasionClassificationException}, which the caller
 * ({@link OccasionInterpretationService}) catches to fall back to
 * {@link RuleBasedOccasionClassifier}. OpenAI response classes/shapes never
 * escape this class.
 */
@Component
public class OpenAiOccasionClassifier implements OccasionClassifier {

    private static final Logger log = LoggerFactory.getLogger(OpenAiOccasionClassifier.class);

    private final OccasionClassifierProperties properties;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    public OpenAiOccasionClassifier(
            OccasionClassifierProperties properties,
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
    public OccasionClassificationResult classify(OccasionClassificationInput input) {
        if (!properties.hasApiKey()) {
            throw new OccasionClassificationException(
                    "Occasion classification is not configured: OPENAI_API_KEY is not set");
        }

        ObjectNode requestBody = buildRequestBody(input);
        JsonNode responseJson = callOpenAi(requestBody);
        return extractResult(responseJson);
    }

    private ObjectNode buildRequestBody(OccasionClassificationInput input) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", properties.openaiModel());
        root.put("input", buildPrompt(input));
        root.put("temperature", 0.2);

        ObjectNode format = root.putObject("text").putObject("format");
        format.put("type", "json_schema");
        format.put("name", "occasion_interpretation");
        format.put("strict", true);
        format.set("schema", buildJsonSchema());

        return root;
    }

    private String buildPrompt(OccasionClassificationInput input) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Interpret the occasion and dress code for the following event, and return only the ")
                .append("schema-compliant JSON object described by the response format. ")
                .append("Event title: ").append(input.eventTitle()).append(". ");

        if (input.eventDescription() != null && !input.eventDescription().isBlank()) {
            prompt.append("Event description: ").append(input.eventDescription()).append(". ");
        }
        prompt.append("Setting: ").append(input.eventSetting()).append(". ");
        if (input.manualDressCode() != null && !input.manualDressCode().isBlank()) {
            prompt.append("Manually entered dress code: ").append(input.manualDressCode()).append(". ");
        }
        if (input.outfitRequest() != null && !input.outfitRequest().isBlank()) {
            prompt.append("Saved outfit request: ").append(input.outfitRequest()).append(". ");
        }
        if (input.preferredStyle() != null) {
            prompt.append("Preferred style: ").append(input.preferredStyle()).append(". ");
        }
        if (!input.preferredColors().isEmpty()) {
            prompt.append("Preferred colors: ").append(String.join(", ", input.preferredColors())).append(". ");
        }
        if (!input.colorsToAvoid().isEmpty()) {
            prompt.append("Colors to avoid: ").append(String.join(", ", input.colorsToAvoid())).append(". ");
        }

        prompt.append("Use UNKNOWN for occasion or dressCode when the text above does not give you enough ")
                .append("evidence - never guess. Do not invent, assume, or look up current or forecasted weather ")
                .append("conditions for this event; only include a weather-related special requirement ")
                .append("(e.g. rain, hot, or cold weather suitability) if it is explicitly implied by the event ")
                .append("text itself, such as an explicitly outdoor setting or a season/condition mentioned in ")
                .append("the title or description. Do not invent product names, URLs, prices, or inventory - ")
                .append("this response only classifies the occasion, it does not select any products.");

        prompt.append(" Additionally, extract requestedItems: identify every specific product phrase the user ")
                .append("explicitly named in the saved outfit request (e.g. \"USA soccer jersey\", \"swim goggles\", ")
                .append("\"hiking boots\"), one entry per distinct item. Preserve each phrase exactly as the user ")
                .append("wrote it in originalPhrase - never rewrite it into a generic garment name and never force it ")
                .append("into a narrower category if that would lose meaning (a soccer jersey is not a dress shirt, ")
                .append("football boots are not loafers, swim goggles are not sunglasses, a swim cap is not a baseball ")
                .append("cap, hiking boots are not dress shoes). Classify genericCategory using only the broad, ")
                .append("activity-agnostic values in the schema (TOP, BOTTOM, ONE_PIECE, FOOTWEAR, OUTERWEAR, ")
                .append("ACCESSORY, EQUIPMENT, OTHER) - never invent a new category value for a specific sport or ")
                .append("garment. Provide a few short searchTerms variants (synonyms/alternate names) to help find ")
                .append("this exact item while shopping, without changing what the item is. Set activityContext to ")
                .append("the relevant activity/sport in the user's own words when evident (e.g. \"soccer\", ")
                .append("\"swimming\", \"hiking\"), or null when none is evident. Do not invent a named brand unless ")
                .append("the user explicitly requested one. If the outfit request names no specific products, return ")
                .append("an empty requestedItems array - never invent items that were not mentioned.");

        return prompt.toString();
    }

    private ObjectNode buildJsonSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);

        ObjectNode properties = schema.putObject("properties");
        properties.set("occasion", enumSchema(OccasionType.values()));
        properties.set("dressCode", enumSchema(InterpretedDressCode.values()));

        ObjectNode formality = properties.putObject("formalityLevel");
        formality.put("type", "integer");
        formality.put("minimum", 1);
        formality.put("maximum", 10);

        properties.set("requiredCategories", enumArraySchema(ProductCategory.values()));
        properties.set("optionalCategories", enumArraySchema(ProductCategory.values()));
        properties.set("preferredColors", stringArraySchema());
        properties.set("colorsToAvoid", stringArraySchema());
        properties.set("specialRequirements", enumArraySchema(SpecialRequirement.values()));
        properties.set("assumptions", stringArraySchema());
        properties.set("requestedItems", requestedItemsSchema());

        ObjectNode confidence = properties.putObject("confidence");
        confidence.put("type", "number");
        confidence.put("minimum", 0);
        confidence.put("maximum", 1);

        ArrayNode required = schema.putArray("required");
        required.add("occasion");
        required.add("dressCode");
        required.add("formalityLevel");
        required.add("requiredCategories");
        required.add("optionalCategories");
        required.add("preferredColors");
        required.add("colorsToAvoid");
        required.add("specialRequirements");
        required.add("assumptions");
        required.add("requestedItems");
        required.add("confidence");

        return schema;
    }

    /**
     * Schema for the {@code requestedItems} array: one object per explicit
     * product phrase, deliberately keeping {@code originalPhrase} as free
     * text (never an enum) so specificity is never lost, while {@code
     * genericCategory} stays restricted to the small, fixed, activity-agnostic
     * set in {@link GenericItemCategory} - no per-sport/per-garment values.
     */
    private ObjectNode requestedItemsSchema() {
        ObjectNode arraySchema = objectMapper.createObjectNode();
        arraySchema.put("type", "array");

        ObjectNode itemSchema = arraySchema.putObject("items");
        itemSchema.put("type", "object");
        itemSchema.put("additionalProperties", false);

        ObjectNode itemProperties = itemSchema.putObject("properties");
        itemProperties.putObject("originalPhrase").put("type", "string");
        itemProperties.set("genericCategory", enumSchema(GenericItemCategory.values()));
        itemProperties.set("searchTerms", stringArraySchema());
        itemProperties.putObject("required").put("type", "boolean");
        ObjectNode activityContext = itemProperties.putObject("activityContext");
        activityContext.putArray("type").add("string").add("null");

        ArrayNode itemRequired = itemSchema.putArray("required");
        itemRequired.add("originalPhrase");
        itemRequired.add("genericCategory");
        itemRequired.add("searchTerms");
        itemRequired.add("required");
        itemRequired.add("activityContext");

        return arraySchema;
    }

    private ObjectNode enumSchema(Enum<?>[] values) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "string");
        ArrayNode enumValues = node.putArray("enum");
        for (Enum<?> value : values) {
            enumValues.add(value.name());
        }
        return node;
    }

    private ObjectNode enumArraySchema(Enum<?>[] values) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "array");
        node.set("items", enumSchema(values));
        return node;
    }

    private ObjectNode stringArraySchema() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "array");
        node.putObject("items").put("type", "string");
        return node;
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
            log.warn("Occasion classifier provider returned HTTP {}", e.getStatusCode());
            throw new OccasionClassificationException(
                    "Occasion classifier provider returned an error: HTTP " + e.getStatusCode(), e);
        } catch (WebClientRequestException e) {
            log.warn("Occasion classifier provider request failed: {}", e.getClass().getSimpleName());
            throw new OccasionClassificationException("Occasion classifier provider request failed", e);
        } catch (RuntimeException e) {
            // Covers timeouts (Mono#block(Duration) throws IllegalStateException when the
            // deadline elapses) and any other unexpected failure invoking the provider.
            log.warn("Occasion classifier provider call failed: {}", e.getClass().getSimpleName());
            throw new OccasionClassificationException("Occasion classifier provider call failed", e);
        }

        if (responseBody == null) {
            throw new OccasionClassificationException("Occasion classifier provider returned an empty response");
        }

        try {
            return objectMapper.readTree(responseBody);
        } catch (JacksonException e) {
            throw new OccasionClassificationException("Occasion classifier provider returned malformed JSON", e);
        }
    }

    // Package-private (rather than private) so OpenAiOccasionClassifierTest can exercise
    // response parsing directly against hand-built JsonNode fixtures, without needing a
    // real or fake HTTP call for every case.
    OccasionClassificationResult extractResult(JsonNode responseJson) {
        if (responseJson == null || !responseJson.isObject()) {
            throw new OccasionClassificationException("Occasion classifier provider returned an unexpected response shape");
        }

        String status = responseJson.path("status").asString(null);
        if ("failed".equals(status)) {
            String message = responseJson.path("error").path("message").asString("unknown provider error");
            throw new OccasionClassificationException("Occasion classifier provider reported failure: " + message);
        }

        JsonNode output = responseJson.path("output");
        if (!output.isArray()) {
            throw new OccasionClassificationException("Occasion classifier provider response had no output");
        }

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
                String text = contentItem.path("text").asString(null);
                if (text == null || text.isBlank()) {
                    continue;
                }
                JsonNode parsed;
                try {
                    parsed = objectMapper.readTree(text);
                } catch (JacksonException e) {
                    throw new OccasionClassificationException(
                            "Occasion classifier provider returned malformed structured output", e);
                }
                return OccasionInterpretationValidator.validate(parsed, properties.openaiModel());
            }
        }

        throw new OccasionClassificationException("Occasion classifier provider response contained no output text");
    }
}
