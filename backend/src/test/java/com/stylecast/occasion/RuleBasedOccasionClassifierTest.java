package com.stylecast.occasion;

import com.stylecast.catalog.ProductCategory;
import com.stylecast.event.EventSetting;
import com.stylecast.event.styling.PreferredStyle;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

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

    // --- Requested-item extraction (Task 8.5) ---

    private List<RequestedItem> requestedItemsFor(String outfitRequest) {
        OccasionClassificationResult result = classifier.classify(
                input("Weekend event", null, EventSetting.OUTDOOR, null, outfitRequest));
        return result.requestedItems();
    }

    @Test
    void classify_soccerOutfitRequest_preservesJerseyPhraseAsTopCategory() {
        List<RequestedItem> items = requestedItemsFor("I want a USA soccer jersey with shorts and football boots.");

        RequestedItem jersey = items.stream().filter(i -> i.originalPhrase().equals("USA soccer jersey")).findFirst().orElseThrow();
        assertThat(jersey.genericCategory()).isEqualTo(GenericItemCategory.TOP);
        assertThat(jersey.activityContext()).isEqualTo("soccer");
    }

    @Test
    void classify_soccerOutfitRequest_bareShortsRemainShortsAndAreClassifiedAsBottom() {
        List<RequestedItem> items = requestedItemsFor("I want a USA soccer jersey with shorts and football boots.");

        RequestedItem shorts = items.stream().filter(i -> i.originalPhrase().toLowerCase().contains("shorts")).findFirst().orElseThrow();
        assertThat(shorts.genericCategory()).isEqualTo(GenericItemCategory.BOTTOM);
        // The exact user word "shorts" must never be replaced with formal trousers.
        assertThat(shorts.originalPhrase()).doesNotContainIgnoringCase("trouser");
    }

    @Test
    void classify_soccerOutfitRequest_footballBootsSearchIncludesSoccerCleatsSynonym() {
        List<RequestedItem> items = requestedItemsFor("I want a USA soccer jersey with shorts and football boots.");

        RequestedItem boots = items.stream().filter(i -> i.originalPhrase().equals("football boots")).findFirst().orElseThrow();
        assertThat(boots.genericCategory()).isEqualTo(GenericItemCategory.FOOTWEAR);
        assertThat(boots.searchTerms()).contains("soccer cleats");
        // Must never be rewritten into loafers/dress shoes.
        assertThat(boots.searchTerms()).noneMatch(term -> term.toLowerCase().contains("loafer"));
    }

    @Test
    void classify_swimmingOutfitRequest_extractsTrunksGogglesAndCap() {
        List<RequestedItem> items = requestedItemsFor("I need swim trunks, swimming goggles, and a swim cap for the pool party.");

        assertThat(items).extracting(RequestedItem::originalPhrase)
                .anyMatch(phrase -> phrase.toLowerCase().contains("trunk"))
                .anyMatch(phrase -> phrase.toLowerCase().contains("goggle"))
                .anyMatch(phrase -> phrase.toLowerCase().contains("cap"));

        RequestedItem trunks = items.stream().filter(i -> i.originalPhrase().toLowerCase().contains("trunk")).findFirst().orElseThrow();
        assertThat(trunks.genericCategory()).isEqualTo(GenericItemCategory.BOTTOM);

        RequestedItem goggles = items.stream().filter(i -> i.originalPhrase().toLowerCase().contains("goggle")).findFirst().orElseThrow();
        assertThat(goggles.genericCategory()).isEqualTo(GenericItemCategory.EQUIPMENT);
        // Must never be rewritten into sunglasses.
        assertThat(goggles.searchTerms()).noneMatch(term -> term.toLowerCase().contains("sunglasses"));

        RequestedItem cap = items.stream().filter(i -> i.originalPhrase().toLowerCase().contains("cap")).findFirst().orElseThrow();
        assertThat(cap.genericCategory()).isEqualTo(GenericItemCategory.ACCESSORY);
        // Must never be rewritten into a baseball cap.
        assertThat(cap.searchTerms()).noneMatch(term -> term.toLowerCase().contains("baseball"));
    }

    @Test
    void classify_hikingOutfitRequest_extractsShirtTrousersBootsAndRainShell() {
        List<RequestedItem> items = requestedItemsFor("I need a hiking shirt, hiking trousers, hiking boots, and a rain shell.");

        RequestedItem shirt = items.stream().filter(i -> i.originalPhrase().equals("hiking shirt")).findFirst().orElseThrow();
        assertThat(shirt.genericCategory()).isEqualTo(GenericItemCategory.TOP);

        RequestedItem trousers = items.stream().filter(i -> i.originalPhrase().equals("hiking trousers")).findFirst().orElseThrow();
        assertThat(trousers.genericCategory()).isEqualTo(GenericItemCategory.BOTTOM);

        RequestedItem boots = items.stream().filter(i -> i.originalPhrase().equals("hiking boots")).findFirst().orElseThrow();
        assertThat(boots.genericCategory()).isEqualTo(GenericItemCategory.FOOTWEAR);
        // Must never be rewritten into dress shoes.
        assertThat(boots.searchTerms()).noneMatch(term -> term.toLowerCase().contains("dress shoe"));

        RequestedItem shell = items.stream().filter(i -> i.originalPhrase().equals("rain shell")).findFirst().orElseThrow();
        assertThat(shell.genericCategory()).isEqualTo(GenericItemCategory.OUTERWEAR);
    }

    @Test
    void classify_weddingOutfitRequest_stillWorksAndExtractsExplicitItems() {
        OccasionClassificationResult result = classifier.classify(
                input("Sarah & Tom's Wedding", null, EventSetting.OUTDOOR, null,
                        "I'll wear a navy suit, a white dress shirt, a tie, and dress shoes."));

        assertThat(result.occasion()).isEqualTo(OccasionType.WEDDING);
        List<RequestedItem> items = result.requestedItems();
        assertThat(items).extracting(RequestedItem::originalPhrase).contains("tie", "dress shoes");
        RequestedItem shirt = items.stream().filter(i -> i.originalPhrase().equals("white dress shirt")).findFirst().orElseThrow();
        assertThat(shirt.genericCategory()).isEqualTo(GenericItemCategory.TOP);
    }

    @Test
    void classify_interviewOutfitRequest_stillWorksAndExtractsExplicitItems() {
        OccasionClassificationResult result = classifier.classify(
                input("Software Engineer Interview", null, EventSetting.INDOOR, null,
                        "I want a blazer, a dress shirt, trousers, and formal shoes."));

        assertThat(result.occasion()).isEqualTo(OccasionType.INTERVIEW);
        List<RequestedItem> items = result.requestedItems();
        assertThat(items).extracting(RequestedItem::originalPhrase).contains("dress shirt", "trousers", "formal shoes");
        RequestedItem blazer = items.stream().filter(i -> i.originalPhrase().equals("blazer")).findFirst().orElseThrow();
        assertThat(blazer.genericCategory()).isEqualTo(GenericItemCategory.OUTERWEAR);
    }

    @Test
    void classify_outfitRequestWithNoSpecificProducts_returnsEmptyRequestedItems() {
        List<RequestedItem> items = requestedItemsFor("Something comfortable and stylish, please.");

        // No brand or product was invented even though the request is vague.
        assertThat(items).isEmpty();
    }

    @Test
    void classify_blankOutfitRequest_returnsEmptyRequestedItems() {
        List<RequestedItem> items = requestedItemsFor(null);

        assertThat(items).isEmpty();
    }

    // --- Multi-item phrase splitting (confirmed bug fix) ---------------------------

    @Test
    void classify_spaceSeparatedMultiItemPhrase_splitsIntoDistinctTopBottomFootwearItems() {
        // Regression test for a confirmed bug: "shirt trousers shoes" (no separator at
        // all) used to collapse into a single FOOTWEAR item and produce three
        // footwear-only looks - it must now split into three distinct requested items.
        List<RequestedItem> items = requestedItemsFor("shirt trousers shoes");

        assertThat(items).extracting(RequestedItem::originalPhrase, RequestedItem::genericCategory)
                .containsExactly(
                        tuple("shirt", GenericItemCategory.TOP),
                        tuple("trousers", GenericItemCategory.BOTTOM),
                        tuple("shoes", GenericItemCategory.FOOTWEAR));
        // Never all three collapsed into FOOTWEAR.
        assertThat(items).extracting(RequestedItem::genericCategory)
                .doesNotContainSequence(GenericItemCategory.FOOTWEAR, GenericItemCategory.FOOTWEAR, GenericItemCategory.FOOTWEAR);
    }

    @Test
    void classify_blazerTrousersLoafers_splitsIntoDistinctOuterwearBottomFootwearItems() {
        List<RequestedItem> items = requestedItemsFor("blazer trousers loafers");

        assertThat(items).extracting(RequestedItem::genericCategory)
                .containsExactly(GenericItemCategory.OUTERWEAR, GenericItemCategory.BOTTOM, GenericItemCategory.FOOTWEAR);
    }

}
