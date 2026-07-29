package com.stylecast.occasion;

import com.stylecast.catalog.ProductCategory;
import com.stylecast.event.EventSetting;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
}
