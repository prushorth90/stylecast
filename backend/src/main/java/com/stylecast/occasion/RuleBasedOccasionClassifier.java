package com.stylecast.occasion;

import com.stylecast.catalog.ProductCategory;
import com.stylecast.event.EventSetting;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic keyword-based {@link OccasionClassifier}. Used whenever
 * {@link OpenAiOccasionClassifier} is unconfigured, fails, times out, or
 * returns invalid output - always available, always testable, and never
 * dependent on an external call.
 *
 * <p>Only matches explicit keywords already present in the event's own
 * text (title, description, manually entered dress code, saved outfit
 * request) - it never calls a weather provider and never guesses beyond
 * what that text supports. When no keyword matches, it returns
 * {@link OccasionType#UNKNOWN} / {@link InterpretedDressCode#UNKNOWN} rather
 * than inventing a classification, and always uses a lower {@code
 * confidence} than a successful AI classification.
 */
@Component
public class RuleBasedOccasionClassifier implements OccasionClassifier {

    private static final BigDecimal CONFIDENCE_WITH_MATCH = new BigDecimal("0.45");
    private static final BigDecimal CONFIDENCE_UNKNOWN = new BigDecimal("0.20");

    private record Rule(
            String keyword,
            OccasionType occasion,
            int formalityLevel,
            InterpretedDressCode defaultDressCode,
            List<ProductCategory> requiredCategories,
            List<ProductCategory> optionalCategories) {
    }

    // Order matters: more specific multi-word phrases are checked before
    // shorter, more generic single-word keywords.
    private static final List<Rule> RULES = List.of(
            new Rule("black tie", OccasionType.FORMAL_EVENT, 10, InterpretedDressCode.BLACK_TIE,
                    List.of(ProductCategory.SUIT, ProductCategory.SHOES), List.of(ProductCategory.ACCESSORY)),
            new Rule("wedding", OccasionType.WEDDING, 8, InterpretedDressCode.COCKTAIL,
                    List.of(ProductCategory.SUIT, ProductCategory.SHOES),
                    List.of(ProductCategory.DRESS, ProductCategory.ACCESSORY, ProductCategory.OUTERWEAR)),
            new Rule("interview", OccasionType.INTERVIEW, 7, InterpretedDressCode.BUSINESS_FORMAL,
                    List.of(ProductCategory.SUIT, ProductCategory.SHIRT, ProductCategory.SHOES),
                    List.of(ProductCategory.ACCESSORY)),
            new Rule("business meeting", OccasionType.BUSINESS_MEETING, 6, InterpretedDressCode.BUSINESS_CASUAL,
                    List.of(ProductCategory.TROUSERS, ProductCategory.SHIRT, ProductCategory.SHOES),
                    List.of(ProductCategory.BLAZER)),
            new Rule("meeting", OccasionType.BUSINESS_MEETING, 6, InterpretedDressCode.BUSINESS_CASUAL,
                    List.of(ProductCategory.TROUSERS, ProductCategory.SHIRT, ProductCategory.SHOES),
                    List.of(ProductCategory.BLAZER)),
            new Rule("networking", OccasionType.NETWORKING, 6, InterpretedDressCode.BUSINESS_CASUAL,
                    List.of(ProductCategory.TROUSERS, ProductCategory.SHIRT, ProductCategory.SHOES),
                    List.of(ProductCategory.BLAZER, ProductCategory.ACCESSORY)),
            new Rule("conference", OccasionType.CONFERENCE, 5, InterpretedDressCode.BUSINESS_CASUAL,
                    List.of(ProductCategory.TROUSERS, ProductCategory.SHIRT, ProductCategory.SHOES),
                    List.of(ProductCategory.BLAZER)),
            new Rule("black-tie", OccasionType.FORMAL_EVENT, 10, InterpretedDressCode.BLACK_TIE,
                    List.of(ProductCategory.SUIT, ProductCategory.SHOES), List.of(ProductCategory.ACCESSORY)),
            new Rule("dinner", OccasionType.DINNER, 6, InterpretedDressCode.SMART_CASUAL,
                    List.of(ProductCategory.SHIRT, ProductCategory.TROUSERS, ProductCategory.SHOES),
                    List.of(ProductCategory.BLAZER, ProductCategory.DRESS, ProductCategory.ACCESSORY)),
            new Rule("date", OccasionType.DATE, 5, InterpretedDressCode.SMART_CASUAL,
                    List.of(ProductCategory.SHIRT, ProductCategory.TROUSERS, ProductCategory.SHOES),
                    List.of(ProductCategory.DRESS, ProductCategory.ACCESSORY)),
            new Rule("cocktail", OccasionType.PARTY, 7, InterpretedDressCode.COCKTAIL,
                    List.of(ProductCategory.SHOES), List.of(ProductCategory.DRESS, ProductCategory.SUIT, ProductCategory.ACCESSORY)),
            new Rule("concert", OccasionType.CONCERT, 3, InterpretedDressCode.CASUAL,
                    List.of(ProductCategory.SHIRT, ProductCategory.TROUSERS, ProductCategory.SHOES),
                    List.of(ProductCategory.OUTERWEAR, ProductCategory.ACCESSORY)),
            new Rule("party", OccasionType.PARTY, 5, InterpretedDressCode.SMART_CASUAL,
                    List.of(ProductCategory.SHIRT, ProductCategory.TROUSERS, ProductCategory.SHOES),
                    List.of(ProductCategory.DRESS, ProductCategory.ACCESSORY)),
            new Rule("formal", OccasionType.FORMAL_EVENT, 8, InterpretedDressCode.FORMAL,
                    List.of(ProductCategory.SUIT, ProductCategory.SHOES), List.of(ProductCategory.ACCESSORY)),
            new Rule("casual", OccasionType.CASUAL_OUTING, 2, InterpretedDressCode.CASUAL,
                    List.of(ProductCategory.SHIRT, ProductCategory.TROUSERS, ProductCategory.SHOES),
                    List.of(ProductCategory.OUTERWEAR))
    );

    @Override
    public OccasionClassificationResult classify(OccasionClassificationInput input) {
        String text = combinedText(input).toLowerCase(Locale.ROOT);

        Rule rule = matchRule(text);
        InterpretedDressCode dressCode = resolveDressCode(input.manualDressCode(), text, rule, input.eventSetting());
        List<SpecialRequirement> specialRequirements = resolveSpecialRequirements(text, input.eventSetting());

        List<String> assumptions = new ArrayList<>();
        assumptions.add("Classified using keyword matching against event text; no live weather data was used.");
        if (rule == null) {
            assumptions.add("No confident occasion keywords were found in the event details.");
        }

        List<RequestedItem> requestedItems = extractRequestedItems(input.outfitRequest());

        return new OccasionClassificationResult(
                rule != null ? rule.occasion() : OccasionType.UNKNOWN,
                dressCode,
                rule != null ? rule.formalityLevel() : 5,
                rule != null ? rule.requiredCategories() : List.of(),
                rule != null ? rule.optionalCategories() : List.of(),
                normalizeStrings(input.preferredColors()),
                normalizeStrings(input.colorsToAvoid()),
                specialRequirements,
                List.copyOf(assumptions),
                requestedItems,
                rule != null ? CONFIDENCE_WITH_MATCH : CONFIDENCE_UNKNOWN,
                InterpretationSource.RULE_BASED_FALLBACK,
                null);
    }

    private Rule matchRule(String text) {
        for (Rule rule : RULES) {
            if (text.contains(rule.keyword())) {
                return rule;
            }
        }
        return null;
    }

    private InterpretedDressCode resolveDressCode(
            String manualDressCode, String combinedText, Rule rule, EventSetting setting) {
        if (manualDressCode != null && !manualDressCode.isBlank()) {
            InterpretedDressCode fromManual = matchDressCodePhrase(manualDressCode.toLowerCase(Locale.ROOT));
            if (fromManual != null) {
                return fromManual;
            }
        }

        if (rule == null) {
            return InterpretedDressCode.UNKNOWN;
        }

        if (rule.occasion() == OccasionType.WEDDING
                && rule.defaultDressCode() == InterpretedDressCode.COCKTAIL
                && setting == EventSetting.OUTDOOR) {
            return InterpretedDressCode.GARDEN_COCKTAIL;
        }
        return rule.defaultDressCode();
    }

    private InterpretedDressCode matchDressCodePhrase(String text) {
        if (text.contains("black tie")) {
            return InterpretedDressCode.BLACK_TIE;
        }
        if (text.contains("garden")) {
            return InterpretedDressCode.GARDEN_COCKTAIL;
        }
        if (text.contains("cocktail")) {
            return InterpretedDressCode.COCKTAIL;
        }
        if (text.contains("business formal")) {
            return InterpretedDressCode.BUSINESS_FORMAL;
        }
        if (text.contains("business casual")) {
            return InterpretedDressCode.BUSINESS_CASUAL;
        }
        if (text.contains("smart casual")) {
            return InterpretedDressCode.SMART_CASUAL;
        }
        if (text.contains("formal")) {
            return InterpretedDressCode.FORMAL;
        }
        if (text.contains("casual")) {
            return InterpretedDressCode.CASUAL;
        }
        return null;
    }

    private List<SpecialRequirement> resolveSpecialRequirements(String text, EventSetting setting) {
        Set<SpecialRequirement> requirements = new LinkedHashSet<>();
        if (setting == EventSetting.OUTDOOR) {
            requirements.add(SpecialRequirement.OUTDOOR_SUITABLE);
            requirements.add(SpecialRequirement.GRASS_FRIENDLY_FOOTWEAR);
        }
        if (text.contains("rain")) {
            requirements.add(SpecialRequirement.RAIN_SUITABLE);
        }
        if (text.contains("hot") || text.contains("summer") || text.contains("heat")) {
            requirements.add(SpecialRequirement.HOT_WEATHER_SUITABLE);
        }
        if (text.contains("cold") || text.contains("winter") || text.contains("chilly")) {
            requirements.add(SpecialRequirement.COLD_WEATHER_SUITABLE);
            requirements.add(SpecialRequirement.LAYER_RECOMMENDED);
        }
        if (text.contains("walk") || text.contains("tour") || text.contains("hike")) {
            requirements.add(SpecialRequirement.COMFORTABLE_FOR_WALKING);
        }
        if (text.contains("relaxed") || text.contains("low-key") || text.contains("low key")) {
            requirements.add(SpecialRequirement.NOT_OVERLY_FORMAL);
        }
        return List.copyOf(requirements);
    }

    // Deliberately excludes manualDressCode: dress-code text (e.g. "black tie") should
    // only ever influence the interpreted dress code (see resolveDressCode), never the
    // occasion type itself - a "Company dinner" with dress code "Black tie" is still a
    // DINNER, just a very formal one.
    private String combinedText(OccasionClassificationInput input) {
        StringBuilder builder = new StringBuilder();
        appendIfPresent(builder, input.eventTitle());
        appendIfPresent(builder, input.eventDescription());
        appendIfPresent(builder, input.outfitRequest());
        return builder.toString();
    }

    private void appendIfPresent(StringBuilder builder, String value) {
        if (value != null && !value.isBlank()) {
            builder.append(' ').append(value);
        }
    }

    private List<String> normalizeStrings(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    // --- Requested-item extraction (Task 8.5) ---
    //
    // Deterministic, activity-agnostic extraction of explicit product phrases from
    // the saved outfitRequest text - deliberately NOT title/description, since those
    // describe the event, not the products the user is asking for. Never adds a new
    // enum value per sport/garment: genericCategory stays within GenericItemCategory's
    // fixed set, and anything sport/activity-specific is preserved as free text
    // (originalPhrase/searchTerms/activityContext) instead. Segmenting text and
    // splitting a segment into one-or-more recognized garment phrases (e.g. "shirt
    // trousers shoes" -> three separate items) is handled by
    // RequestedItemPhraseSplitter; this class only detects activity context and
    // builds search-term variants for whatever phrases come back.

    // Free-text activity detection only - never a new enum value per sport.
    private static final List<Map.Entry<String, String>> ACTIVITY_KEYWORDS = List.of(
            Map.entry("soccer", "soccer"),
            Map.entry("swimming", "swimming"),
            Map.entry("swim", "swimming"),
            Map.entry("hiking", "hiking"),
            Map.entry("hike", "hiking"),
            Map.entry("running", "running"),
            Map.entry("golf", "golf"),
            Map.entry("tennis", "tennis"),
            Map.entry("skiing", "skiing"),
            Map.entry("cycling", "cycling"),
            Map.entry("camping", "camping"),
            Map.entry("yoga", "yoga"));

    private static final Map<String, List<String>> PHRASE_SEARCH_SYNONYMS = Map.ofEntries(
            Map.entry("football boots", List.of("football boots", "soccer cleats", "soccer boots")),
            Map.entry("soccer cleats", List.of("soccer cleats", "football boots", "soccer boots")),
            Map.entry("soccer boots", List.of("soccer boots", "football boots", "soccer cleats")),
            Map.entry("swim trunks", List.of("swim trunks", "swimming trunks", "board shorts")),
            Map.entry("swim cap", List.of("swim cap", "swimming cap")),
            Map.entry("swim goggles", List.of("swim goggles", "swimming goggles")),
            Map.entry("swimming goggles", List.of("swimming goggles", "swim goggles")),
            Map.entry("pool slides", List.of("pool slides", "shower sandals")),
            Map.entry("hiking boots", List.of("hiking boots", "trail boots", "hiking shoes")),
            Map.entry("rain shell", List.of("rain shell", "rain jacket", "waterproof jacket")),
            Map.entry("dress shirt", List.of("dress shirt", "button-up shirt", "button-down shirt")),
            Map.entry("dress shoes", List.of("dress shoes", "oxfords", "derbies")),
            Map.entry("formal shoes", List.of("formal shoes", "dress shoes", "oxfords")));

    /**
     * Extracts explicit product phrases from the user's own {@code
     * outfitRequest} text - never from title/description, and never
     * invented when the request names nothing specific (returns an empty
     * list in that case).
     */
    private List<RequestedItem> extractRequestedItems(String outfitRequest) {
        if (outfitRequest == null || outfitRequest.isBlank()) {
            return List.of();
        }

        String lowerRequest = outfitRequest.toLowerCase(Locale.ROOT);
        String activityContext = detectActivityContext(lowerRequest);

        List<RequestedItem> items = new ArrayList<>();
        int displayOrder = 0;
        for (RequestedItemPhraseSplitter.SplitItem splitItem : RequestedItemPhraseSplitter.splitText(outfitRequest, activityContext)) {
            List<String> searchTerms = buildSearchTerms(splitItem.phrase(), activityContext);
            RequestedItem item = RequestedItemNormalizer.normalize(
                    splitItem.phrase(), splitItem.category(), searchTerms, true, activityContext, displayOrder);
            if (item != null) {
                items.add(item);
                displayOrder++;
            }
        }
        return List.copyOf(items);
    }

    private String detectActivityContext(String lowerText) {
        for (Map.Entry<String, String> entry : ACTIVITY_KEYWORDS) {
            if (lowerText.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Never invents a brand or product beyond what was asked - only ever
     * returns the phrase itself plus deterministic, hand-curated synonym
     * variants (e.g. "football boots" also searching "soccer cleats") to
     * improve live-search recall, exactly mirroring how {@code
     * CategorySynonyms} already augments the category-template pipeline.
     */
    private List<String> buildSearchTerms(String phrase, String activityContext) {
        String lower = phrase.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, List<String>> entry : PHRASE_SEARCH_SYNONYMS.entrySet()) {
            if (lower.contains(entry.getKey())) {
                List<String> terms = new ArrayList<>();
                terms.add(phrase);
                terms.addAll(entry.getValue());
                return terms;
            }
        }
        List<String> terms = new ArrayList<>();
        terms.add(phrase);
        if (activityContext != null && !lower.contains(activityContext.toLowerCase(Locale.ROOT))) {
            terms.add(activityContext + " " + phrase);
        }
        return terms;
    }
}
