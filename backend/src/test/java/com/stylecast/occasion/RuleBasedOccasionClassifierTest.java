package com.stylecast.occasion;

import com.stylecast.catalog.ProductCategory;
import com.stylecast.event.EventSetting;
import com.stylecast.event.styling.PreferredStyle;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedOccasionClassifierTest {

    private final RuleBasedOccasionClassifier classifier = new RuleBasedOccasionClassifier();

    private OccasionClassificationInput input(
            String title, String description, EventSetting setting, String dressCode, String outfitRequest) {
        return new OccasionClassificationInput(
                title, description, setting, dressCode, outfitRequest, PreferredStyle.CLASSIC,
                List.of("navy"), List.of("neon green"));
    }

    @Test
    void classify_weddingOutdoors_returnsWeddingWithGardenCocktailAndOutdoorRequirements() {
        OccasionClassificationResult result = classifier.classify(
                input("Sarah & Tom's Wedding", "An outdoor garden ceremony and reception", EventSetting.OUTDOOR,
                        null, null));

        assertThat(result.occasion()).isEqualTo(OccasionType.WEDDING);
        assertThat(result.dressCode()).isEqualTo(InterpretedDressCode.GARDEN_COCKTAIL);
        assertThat(result.formalityLevel()).isBetween(1, 10);
        assertThat(result.requiredCategories()).contains(ProductCategory.SHOES);
        assertThat(result.specialRequirements())
                .contains(SpecialRequirement.OUTDOOR_SUITABLE, SpecialRequirement.GRASS_FRIENDLY_FOOTWEAR);
        assertThat(result.source()).isEqualTo(InterpretationSource.RULE_BASED_FALLBACK);
        assertThat(result.modelName()).isNull();
        assertThat(result.preferredColors()).containsExactly("navy");
        assertThat(result.colorsToAvoid()).containsExactly("neon green");
    }

    @Test
    void classify_interview_returnsInterviewWithBusinessFormalDressCode() {
        OccasionClassificationResult result = classifier.classify(
                input("Software Engineer Interview at Acme Corp", null, EventSetting.INDOOR, null, null));

        assertThat(result.occasion()).isEqualTo(OccasionType.INTERVIEW);
        assertThat(result.dressCode()).isEqualTo(InterpretedDressCode.BUSINESS_FORMAL);
        assertThat(result.requiredCategories()).contains(ProductCategory.SUIT, ProductCategory.SHOES);
        assertThat(result.source()).isEqualTo(InterpretationSource.RULE_BASED_FALLBACK);
    }

    @Test
    void classify_vagueEvent_returnsUnknownRatherThanGuessing() {
        OccasionClassificationResult result = classifier.classify(
                input("Get together", null, EventSetting.INDOOR, null, null));

        assertThat(result.occasion()).isEqualTo(OccasionType.UNKNOWN);
        assertThat(result.dressCode()).isEqualTo(InterpretedDressCode.UNKNOWN);
        assertThat(result.requiredCategories()).isEmpty();
        assertThat(result.optionalCategories()).isEmpty();
    }

    @Test
    void classify_alwaysUsesLowerConfidenceThanASuccessfulAiResult() {
        OccasionClassificationResult matched = classifier.classify(
                input("Wedding", null, EventSetting.INDOOR, null, null));
        OccasionClassificationResult unknown = classifier.classify(
                input("Get together", null, EventSetting.INDOOR, null, null));

        // A typical successful AI classification carries confidence around 0.7-0.95;
        // the rule-based fallback must always read as clearly lower-confidence.
        assertThat(matched.confidence()).isLessThan(new java.math.BigDecimal("0.7"));
        assertThat(unknown.confidence()).isLessThan(matched.confidence());
    }

    @Test
    void classify_explicitManualDressCodeOverridesKeywordDefault() {
        OccasionClassificationResult result = classifier.classify(
                input("Company dinner", null, EventSetting.INDOOR, "Black tie", null));

        assertThat(result.occasion()).isEqualTo(OccasionType.DINNER);
        assertThat(result.dressCode()).isEqualTo(InterpretedDressCode.BLACK_TIE);
    }

    @Test
    void classify_neverThrows() {
        OccasionClassificationInput blankInput = new OccasionClassificationInput(
                "", null, EventSetting.INDOOR, null, null, null, null, null);

        OccasionClassificationResult result = classifier.classify(blankInput);

        assertThat(result.source()).isEqualTo(InterpretationSource.RULE_BASED_FALLBACK);
    }
}
