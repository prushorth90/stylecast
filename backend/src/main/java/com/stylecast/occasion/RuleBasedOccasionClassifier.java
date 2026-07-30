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
import java.util.regex.Pattern;

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
    // (originalPhrase/searchTerms/activityContext) instead.

    private static final Pattern LEADING_FILLER = Pattern.compile(
            "^(i want|i need|i'd like|i would like|i will wear|i'll wear|i am wearing|i'm wearing|"
                    + "planning to wear|looking for|need|want|wear|wearing|get me|bring|pack)\\s+",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LEADING_ARTICLE = Pattern.compile("^(a|an|the|some|my)\\s+", Pattern.CASE_INSENSITIVE);
    // ",\s*and\s+" (an Oxford-comma "and") is checked before the plain comma/and
    // alternatives so it consumes both together, avoiding a stray leading "and" on the
    // next segment (e.g. "...tie, and dress shoes" splits into "tie"/"dress shoes", not
    // "tie"/"and dress shoes").
    private static final Pattern SEGMENT_SPLIT = Pattern.compile(
            "\\s*,\\s*and\\s+|\\s*,\\s*|\\s+and\\s+|\\s+with\\s+|\\s+plus\\s+|\\s+as well as\\s+|;\\s*",
            Pattern.CASE_INSENSITIVE);

    // Checked in order, longest/most specific phrases first, before falling back to
    // single-keyword matching below - this is what keeps e.g. "dress shirt" a TOP
    // rather than falling into the generic "dress" -> ONE_PIECE bucket.
    private static final List<Map.Entry<String, GenericItemCategory>> COMPOUND_CATEGORY_OVERRIDES = List.of(
            Map.entry("swim cap", GenericItemCategory.ACCESSORY),
            Map.entry("swimming cap", GenericItemCategory.ACCESSORY),
            Map.entry("swim trunks", GenericItemCategory.BOTTOM),
            Map.entry("swim goggles", GenericItemCategory.EQUIPMENT),
            Map.entry("swimming goggles", GenericItemCategory.EQUIPMENT),
            Map.entry("pool slides", GenericItemCategory.FOOTWEAR),
            Map.entry("football boots", GenericItemCategory.FOOTWEAR),
            Map.entry("soccer cleats", GenericItemCategory.FOOTWEAR),
            Map.entry("soccer boots", GenericItemCategory.FOOTWEAR),
            Map.entry("hiking boots", GenericItemCategory.FOOTWEAR),
            Map.entry("hiking shoes", GenericItemCategory.FOOTWEAR),
            Map.entry("hiking trousers", GenericItemCategory.BOTTOM),
            Map.entry("hiking pants", GenericItemCategory.BOTTOM),
            Map.entry("hiking shirt", GenericItemCategory.TOP),
            Map.entry("rain shell", GenericItemCategory.OUTERWEAR),
            Map.entry("dress shirt", GenericItemCategory.TOP),
            Map.entry("dress shoes", GenericItemCategory.FOOTWEAR),
            Map.entry("dress pants", GenericItemCategory.BOTTOM),
            Map.entry("formal shoes", GenericItemCategory.FOOTWEAR));

    private static final List<String> ONE_PIECE_KEYWORDS =
            List.of("jumpsuit", "romper", "onesie", "wetsuit", "swimsuit", "bodysuit", "dress");
    private static final List<String> FOOTWEAR_KEYWORDS =
            List.of("boot", "shoe", "sneaker", "slide", "sandal", "flat", "heel", "loafer", "trainer", "cleat");
    private static final List<String> BOTTOM_KEYWORDS =
            List.of("short", "trouser", "pant", "skirt", "jean", "legging", "trunk");
    private static final List<String> OUTERWEAR_KEYWORDS =
            List.of("jacket", "coat", "shell", "parka", "blazer", "cardigan", "hoodie");
    private static final List<String> TOP_KEYWORDS =
            List.of("shirt", "jersey", "blouse", "sweater", "polo", "tee", "tank", "top", "jumper");
    private static final List<String> ACCESSORY_KEYWORDS =
            List.of("tie", "belt", "cap", "hat", "scarf", "glove", "sock", "sunglasses", "jewelry", "watch");
    private static final List<String> EQUIPMENT_KEYWORDS =
            List.of("goggle", "pad", "guard", "helmet", "ball", "racket", "club", "gear");
    // A full outfit/garment-set phrase (e.g. "navy suit") is a genuine, recognized
    // product phrase even though it doesn't fit TOP/BOTTOM/ONE_PIECE cleanly - kept
    // separate from the "nothing matched" case below (see classifyGenericCategory).
    private static final List<String> OTHER_KEYWORDS = List.of("suit", "tuxedo", "costume", "uniform");

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

    private static final Set<String> BARE_GENERIC_TERMS = Set.of(
            "shorts", "shirt", "boots", "shoes", "pants", "trousers", "jersey", "cap", "goggles",
            "jacket", "socks", "gloves", "trunks", "slides", "sandals");

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
        for (String rawSegment : splitIntoPhrases(outfitRequest)) {
            String phrase = applyActivityContextPrefix(stripFillerWords(rawSegment), activityContext);
            if (phrase.isBlank()) {
                continue;
            }
            GenericItemCategory category = classifyGenericCategory(phrase.toLowerCase(Locale.ROOT));
            if (category == null) {
                // Not a recognized product phrase (e.g. "something comfortable", "please") -
                // skip it rather than inventing an item that was never actually requested.
                continue;
            }
            List<String> searchTerms = buildSearchTerms(phrase, activityContext);
            RequestedItem item = RequestedItemNormalizer.normalize(
                    phrase, category, searchTerms, true, activityContext, displayOrder);
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

    private List<String> splitIntoPhrases(String outfitRequest) {
        String trimmed = outfitRequest.trim().replaceAll("[.!]+$", "");
        List<String> phrases = new ArrayList<>();
        for (String raw : SEGMENT_SPLIT.split(trimmed)) {
            String segment = raw.trim();
            if (!segment.isEmpty()) {
                phrases.add(segment);
            }
        }
        return phrases;
    }

    private String stripFillerWords(String phrase) {
        String result = LEADING_FILLER.matcher(phrase.trim()).replaceFirst("").trim();
        result = LEADING_ARTICLE.matcher(result).replaceFirst("").trim();
        return result;
    }

    /**
     * Enhances a bare, otherwise-ambiguous generic noun (e.g. "shorts" on
     * its own) with the detected activity context (e.g. "soccer shorts"),
     * but leaves an already-descriptive phrase (e.g. "football boots",
     * "USA soccer jersey") untouched - preserving the user's exact words is
     * always preferred over rewriting them.
     */
    private String applyActivityContextPrefix(String phrase, String activityContext) {
        if (activityContext == null || phrase.isBlank()) {
            return phrase;
        }
        String lower = phrase.toLowerCase(Locale.ROOT);
        if (BARE_GENERIC_TERMS.contains(lower) && !lower.contains(activityContext.toLowerCase(Locale.ROOT))) {
            return activityContext + " " + phrase;
        }
        return phrase;
    }

    /**
     * Returns {@code null} when the phrase matches no recognized garment/
     * equipment keyword at all - callers must treat that as "not a product
     * phrase" and skip it, never defaulting to {@link GenericItemCategory#OTHER}
     * for genuinely unrecognized filler text.
     */
    private GenericItemCategory classifyGenericCategory(String lowerPhrase) {
        for (Map.Entry<String, GenericItemCategory> override : COMPOUND_CATEGORY_OVERRIDES) {
            if (lowerPhrase.contains(override.getKey())) {
                return override.getValue();
            }
        }
        if (containsAny(lowerPhrase, ONE_PIECE_KEYWORDS)) {
            return GenericItemCategory.ONE_PIECE;
        }
        if (containsAny(lowerPhrase, FOOTWEAR_KEYWORDS)) {
            return GenericItemCategory.FOOTWEAR;
        }
        if (containsAny(lowerPhrase, BOTTOM_KEYWORDS)) {
            return GenericItemCategory.BOTTOM;
        }
        if (containsAny(lowerPhrase, OUTERWEAR_KEYWORDS)) {
            return GenericItemCategory.OUTERWEAR;
        }
        if (containsAny(lowerPhrase, TOP_KEYWORDS)) {
            return GenericItemCategory.TOP;
        }
        if (containsAny(lowerPhrase, ACCESSORY_KEYWORDS)) {
            return GenericItemCategory.ACCESSORY;
        }
        if (containsAny(lowerPhrase, EQUIPMENT_KEYWORDS)) {
            return GenericItemCategory.EQUIPMENT;
        }
        if (containsAny(lowerPhrase, OTHER_KEYWORDS)) {
            return GenericItemCategory.OTHER;
        }
        return null;
    }

    private boolean containsAny(String lowerPhrase, List<String> keywords) {
        for (String keyword : keywords) {
            if (lowerPhrase.contains(keyword)) {
                return true;
            }
        }
        return false;
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
