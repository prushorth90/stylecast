package com.stylecast.occasion;

import com.stylecast.catalog.ProductCategory;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OccasionInterpretationValidatorTest {

    private static final JsonMapper MAPPER = new JsonMapper();

    private static final String VALID_JSON = """
            {
              "occasion": "WEDDING",
              "dressCode": "COCKTAIL",
              "formalityLevel": 8,
              "requiredCategories": ["SUIT", "SHOES"],
              "optionalCategories": ["ACCESSORY"],
              "preferredColors": ["navy"],
              "colorsToAvoid": ["bright red"],
              "specialRequirements": ["OUTDOOR_SUITABLE"],
              "assumptions": ["Guessed formal wedding based on the title."],
              "confidence": 0.87
            }
            """;

    @Test
    void validate_withValidJson_returnsNormalizedResult() {
        OccasionClassificationResult result =
                OccasionInterpretationValidator.validate(MAPPER.readTree(VALID_JSON), "test-model");

        assertThat(result.occasion()).isEqualTo(OccasionType.WEDDING);
        assertThat(result.dressCode()).isEqualTo(InterpretedDressCode.COCKTAIL);
        assertThat(result.formalityLevel()).isEqualTo(8);
        assertThat(result.requiredCategories()).containsExactly(ProductCategory.SUIT, ProductCategory.SHOES);
        assertThat(result.optionalCategories()).containsExactly(ProductCategory.ACCESSORY);
        assertThat(result.preferredColors()).containsExactly("navy");
        assertThat(result.colorsToAvoid()).containsExactly("bright red");
        assertThat(result.specialRequirements()).containsExactly(SpecialRequirement.OUTDOOR_SUITABLE);
        assertThat(result.assumptions()).containsExactly("Guessed formal wedding based on the title.");
        assertThat(result.confidence()).isEqualByComparingTo("0.87");
        assertThat(result.source()).isEqualTo(InterpretationSource.AI);
        assertThat(result.modelName()).isEqualTo("test-model");
    }

    @Test
    void validate_withFormalityBelowRange_throws() {
        String json = VALID_JSON.replace("\"formalityLevel\": 8", "\"formalityLevel\": 0");

        assertThatThrownBy(() -> OccasionInterpretationValidator.validate(MAPPER.readTree(json), "test-model"))
                .isInstanceOf(OccasionClassificationException.class)
                .hasMessageContaining("formalityLevel");
    }

    @Test
    void validate_withFormalityAboveRange_throws() {
        String json = VALID_JSON.replace("\"formalityLevel\": 8", "\"formalityLevel\": 11");

        assertThatThrownBy(() -> OccasionInterpretationValidator.validate(MAPPER.readTree(json), "test-model"))
                .isInstanceOf(OccasionClassificationException.class)
                .hasMessageContaining("formalityLevel");
    }

    @Test
    void validate_withConfidenceBelowRange_throws() {
        String json = VALID_JSON.replace("\"confidence\": 0.87", "\"confidence\": -0.1");

        assertThatThrownBy(() -> OccasionInterpretationValidator.validate(MAPPER.readTree(json), "test-model"))
                .isInstanceOf(OccasionClassificationException.class)
                .hasMessageContaining("confidence");
    }

    @Test
    void validate_withConfidenceAboveRange_throws() {
        String json = VALID_JSON.replace("\"confidence\": 0.87", "\"confidence\": 1.5");

        assertThatThrownBy(() -> OccasionInterpretationValidator.validate(MAPPER.readTree(json), "test-model"))
                .isInstanceOf(OccasionClassificationException.class)
                .hasMessageContaining("confidence");
    }

    @Test
    void validate_withUnknownRequiredCategory_throws() {
        String json = VALID_JSON.replace("\"requiredCategories\": [\"SUIT\", \"SHOES\"]",
                "\"requiredCategories\": [\"SUIT\", \"SHOE\"]");

        assertThatThrownBy(() -> OccasionInterpretationValidator.validate(MAPPER.readTree(json), "test-model"))
                .isInstanceOf(OccasionClassificationException.class)
                .hasMessageContaining("requiredCategories");
    }

    @Test
    void validate_withUnknownOccasion_throws() {
        String json = VALID_JSON.replace("\"occasion\": \"WEDDING\"", "\"occasion\": \"BAR_MITZVAH\"");

        assertThatThrownBy(() -> OccasionInterpretationValidator.validate(MAPPER.readTree(json), "test-model"))
                .isInstanceOf(OccasionClassificationException.class)
                .hasMessageContaining("occasion");
    }

    @Test
    void validate_withUnknownDressCode_throws() {
        String json = VALID_JSON.replace("\"dressCode\": \"COCKTAIL\"", "\"dressCode\": \"SUPER_FANCY\"");

        assertThatThrownBy(() -> OccasionInterpretationValidator.validate(MAPPER.readTree(json), "test-model"))
                .isInstanceOf(OccasionClassificationException.class)
                .hasMessageContaining("dressCode");
    }

    @Test
    void validate_withMissingRequiredField_throws() {
        String json = """
                {
                  "occasion": "WEDDING",
                  "dressCode": "COCKTAIL"
                }
                """;

        assertThatThrownBy(() -> OccasionInterpretationValidator.validate(MAPPER.readTree(json), "test-model"))
                .isInstanceOf(OccasionClassificationException.class);
    }

    @Test
    void validate_withNonObjectJson_throws() {
        assertThatThrownBy(() -> OccasionInterpretationValidator.validate(MAPPER.readTree("[1,2,3]"), "test-model"))
                .isInstanceOf(OccasionClassificationException.class);
    }

    @Test
    void validate_withValidJson_requestedItemsDefaultsToEmptyWhenFieldAbsent() {
        OccasionClassificationResult result =
                OccasionInterpretationValidator.validate(MAPPER.readTree(VALID_JSON), "test-model");

        assertThat(result.requestedItems()).isEmpty();
    }

    private static final String JSON_WITH_REQUESTED_ITEMS = VALID_JSON.replace(
            "\"confidence\": 0.87",
            """
            "requestedItems": [
              {
                "originalPhrase": "USA soccer jersey",
                "genericCategory": "TOP",
                "searchTerms": ["USA soccer jersey", "soccer jersey"],
                "required": true,
                "activityContext": "soccer"
              },
              {
                "originalPhrase": "football boots",
                "genericCategory": "FOOTWEAR",
                "searchTerms": ["football boots", "soccer cleats"],
                "required": true,
                "activityContext": "soccer"
              }
            ],
            "confidence": 0.87""");

    @Test
    void validate_withRequestedItems_parsesEveryFieldForEachItem() {
        OccasionClassificationResult result =
                OccasionInterpretationValidator.validate(MAPPER.readTree(JSON_WITH_REQUESTED_ITEMS), "test-model");

        assertThat(result.requestedItems()).hasSize(2);
        RequestedItem jersey = result.requestedItems().get(0);
        assertThat(jersey.originalPhrase()).isEqualTo("USA soccer jersey");
        assertThat(jersey.genericCategory()).isEqualTo(GenericItemCategory.TOP);
        assertThat(jersey.searchTerms()).contains("USA soccer jersey", "soccer jersey");
        assertThat(jersey.required()).isTrue();
        assertThat(jersey.activityContext()).isEqualTo("soccer");

        RequestedItem boots = result.requestedItems().get(1);
        assertThat(boots.originalPhrase()).isEqualTo("football boots");
        assertThat(boots.genericCategory()).isEqualTo(GenericItemCategory.FOOTWEAR);
        assertThat(boots.searchTerms()).contains("soccer cleats");
    }

    @Test
    void validate_withUnknownRequestedItemGenericCategory_throws() {
        String json = JSON_WITH_REQUESTED_ITEMS.replace("\"genericCategory\": \"TOP\"", "\"genericCategory\": \"JERSEY_TOP\"");

        assertThatThrownBy(() -> OccasionInterpretationValidator.validate(MAPPER.readTree(json), "test-model"))
                .isInstanceOf(OccasionClassificationException.class)
                .hasMessageContaining("genericCategory");
    }

    @Test
    void validate_withBlankOriginalPhraseInOneRequestedItem_skipsOnlyThatItem() {
        String json = JSON_WITH_REQUESTED_ITEMS.replace(
                "\"originalPhrase\": \"football boots\"", "\"originalPhrase\": \"\"");

        OccasionClassificationResult result =
                OccasionInterpretationValidator.validate(MAPPER.readTree(json), "test-model");

        assertThat(result.requestedItems()).hasSize(1);
        assertThat(result.requestedItems().get(0).originalPhrase()).isEqualTo("USA soccer jersey");
    }

    // --- Deterministic splitting as a safety net over AI-merged phrases -------------

    @Test
    void validate_whenModelMergesMultipleGarmentsIntoOneItem_splitsThemDeterministically() {
        // Regression test for a confirmed bug: the model (like the old rule-based
        // fallback) can occasionally return one merged item (here mis-categorized as
        // FOOTWEAR, mirroring the actual observed bug) for what are really three
        // distinct requested garments - it must be split rather than trusted as-is.
        String json = VALID_JSON.replace(
                "\"confidence\": 0.87",
                """
                "requestedItems": [
                  {
                    "originalPhrase": "shirt trousers shoes",
                    "genericCategory": "FOOTWEAR",
                    "searchTerms": ["shirt trousers shoes"],
                    "required": true
                  }
                ],
                "confidence": 0.87""");

        OccasionClassificationResult result =
                OccasionInterpretationValidator.validate(MAPPER.readTree(json), "test-model");

        assertThat(result.requestedItems()).extracting(RequestedItem::originalPhrase, RequestedItem::genericCategory)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple("shirt", GenericItemCategory.TOP),
                        org.assertj.core.api.Assertions.tuple("trousers", GenericItemCategory.BOTTOM),
                        org.assertj.core.api.Assertions.tuple("shoes", GenericItemCategory.FOOTWEAR));
    }

    @Test
    void validate_whenModelReturnsARecognizedSingleItem_trustsTheModelsOwnCategory() {
        // Deterministic splitting only kicks in for an actual multi-garment merge -
        // a normal single item is never second-guessed.
        OccasionClassificationResult result =
                OccasionInterpretationValidator.validate(MAPPER.readTree(JSON_WITH_REQUESTED_ITEMS), "test-model");

        assertThat(result.requestedItems()).hasSize(2);
    }

    @Test
    void validate_whenModelReturnsAPhraseWithNoRecognizedKeyword_trustsTheModelAsIs() {
        // A single recognized item (whether or not our deterministic dictionary
        // happens to recognize any of its words) is never split or second-guessed -
        // the model's own phrase/category are kept as-is.
        String json = VALID_JSON.replace(
                "\"confidence\": 0.87",
                """
                "requestedItems": [
                  {
                    "originalPhrase": "vintage band t-shirt",
                    "genericCategory": "TOP",
                    "searchTerms": ["vintage band t-shirt"],
                    "required": true
                  }
                ],
                "confidence": 0.87""");

        OccasionClassificationResult result =
                OccasionInterpretationValidator.validate(MAPPER.readTree(json), "test-model");

        assertThat(result.requestedItems()).hasSize(1);
        assertThat(result.requestedItems().get(0).originalPhrase()).isEqualTo("vintage band t-shirt");
        assertThat(result.requestedItems().get(0).genericCategory()).isEqualTo(GenericItemCategory.TOP);
    }
}

