package com.stylecast.occasion;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Pure unit tests for {@link RequestedItemPhraseSplitter} - the fix for a
 * confirmed bug where a merged, no-separator phrase like {@code "shirt
 * trousers shoes"} collapsed into a single {@code FOOTWEAR} item because
 * the old whole-phrase classifier picked whichever category's keyword
 * happened to be checked first.
 */
class RequestedItemPhraseSplitterTest {

    @Test
    void splitText_spaceSeparatedMultiItemPhrase_splitsIntoThreeDistinctCategories() {
        List<RequestedItemPhraseSplitter.SplitItem> items =
                RequestedItemPhraseSplitter.splitText("shirt trousers shoes", null);

        assertThat(items).extracting(RequestedItemPhraseSplitter.SplitItem::phrase, RequestedItemPhraseSplitter.SplitItem::category)
                .containsExactly(
                        tuple("shirt", GenericItemCategory.TOP),
                        tuple("trousers", GenericItemCategory.BOTTOM),
                        tuple("shoes", GenericItemCategory.FOOTWEAR));
    }

    @Test
    void splitText_commaAndAndSeparatedPhrase_splitsIntoTheSameThreeItems() {
        List<RequestedItemPhraseSplitter.SplitItem> items =
                RequestedItemPhraseSplitter.splitText("shirt, trousers and shoes", null);

        assertThat(items).extracting(RequestedItemPhraseSplitter.SplitItem::phrase, RequestedItemPhraseSplitter.SplitItem::category)
                .containsExactly(
                        tuple("shirt", GenericItemCategory.TOP),
                        tuple("trousers", GenericItemCategory.BOTTOM),
                        tuple("shoes", GenericItemCategory.FOOTWEAR));
    }

    @Test
    void splitText_blazerTrousersLoafers_splitsIntoOuterwearBottomFootwear() {
        List<RequestedItemPhraseSplitter.SplitItem> items =
                RequestedItemPhraseSplitter.splitText("blazer trousers loafers", null);

        assertThat(items).extracting(RequestedItemPhraseSplitter.SplitItem::phrase, RequestedItemPhraseSplitter.SplitItem::category)
                .containsExactly(
                        tuple("blazer", GenericItemCategory.OUTERWEAR),
                        tuple("trousers", GenericItemCategory.BOTTOM),
                        tuple("loafers", GenericItemCategory.FOOTWEAR));
    }

    @Test
    void splitText_usaSoccerJerseyShortsSoccerBoots_preservesMultiWordPhrasesAndSplitsCorrectly() {
        List<RequestedItemPhraseSplitter.SplitItem> items =
                RequestedItemPhraseSplitter.splitText("USA soccer jersey shorts soccer boots", "soccer");

        // "shorts" alone is enhanced with the detected activity context ("soccer shorts") the
        // same way the pre-existing "USA soccer jersey with shorts and football boots" example
        // already does - this is about SPLITTING, not about suppressing that existing behavior.
        assertThat(items).extracting(RequestedItemPhraseSplitter.SplitItem::phrase, RequestedItemPhraseSplitter.SplitItem::category)
                .containsExactly(
                        tuple("USA soccer jersey", GenericItemCategory.TOP),
                        tuple("soccer shorts", GenericItemCategory.BOTTOM),
                        tuple("soccer boots", GenericItemCategory.FOOTWEAR));
    }

    @Test
    void splitText_whiteDressShirtBlackTrousersBrownDressShoes_preservesColorModifiersPerItem() {
        List<RequestedItemPhraseSplitter.SplitItem> items = RequestedItemPhraseSplitter.splitText(
                "white dress shirt black trousers brown dress shoes", null);

        assertThat(items).extracting(RequestedItemPhraseSplitter.SplitItem::phrase, RequestedItemPhraseSplitter.SplitItem::category)
                .containsExactly(
                        tuple("white dress shirt", GenericItemCategory.TOP),
                        tuple("black trousers", GenericItemCategory.BOTTOM),
                        tuple("brown dress shoes", GenericItemCategory.FOOTWEAR));
    }

    @Test
    void splitText_swimTrunksGogglesPoolSlides_splitsIntoBottomEquipmentFootwear() {
        List<RequestedItemPhraseSplitter.SplitItem> items =
                RequestedItemPhraseSplitter.splitText("swim trunks goggles pool slides", "swimming");

        // "goggles" alone is enhanced with the detected activity context ("swimming goggles"),
        // the same pre-existing behavior already covered by other activity-context tests.
        assertThat(items).extracting(RequestedItemPhraseSplitter.SplitItem::phrase, RequestedItemPhraseSplitter.SplitItem::category)
                .containsExactly(
                        tuple("swim trunks", GenericItemCategory.BOTTOM),
                        tuple("swimming goggles", GenericItemCategory.EQUIPMENT),
                        tuple("pool slides", GenericItemCategory.FOOTWEAR));
    }

    @Test
    void splitText_preservesKnownMultiWordPhrasesAsSingleItems() {
        assertThat(RequestedItemPhraseSplitter.splitText("soccer boots", null))
                .extracting(RequestedItemPhraseSplitter.SplitItem::phrase)
                .containsExactly("soccer boots");
        assertThat(RequestedItemPhraseSplitter.splitText("dress shoes", null))
                .extracting(RequestedItemPhraseSplitter.SplitItem::phrase)
                .containsExactly("dress shoes");
        assertThat(RequestedItemPhraseSplitter.splitText("white dress shirt", null))
                .extracting(RequestedItemPhraseSplitter.SplitItem::phrase)
                .containsExactly("white dress shirt");
        assertThat(RequestedItemPhraseSplitter.splitText("USA soccer jersey", "soccer"))
                .extracting(RequestedItemPhraseSplitter.SplitItem::phrase)
                .containsExactly("USA soccer jersey");
        assertThat(RequestedItemPhraseSplitter.splitText("pool slides", null))
                .extracting(RequestedItemPhraseSplitter.SplitItem::phrase)
                .containsExactly("pool slides");
        assertThat(RequestedItemPhraseSplitter.splitText("swim trunks", null))
                .extracting(RequestedItemPhraseSplitter.SplitItem::phrase)
                .containsExactly("swim trunks");
        assertThat(RequestedItemPhraseSplitter.splitText("leather jacket", null))
                .extracting(RequestedItemPhraseSplitter.SplitItem::phrase, RequestedItemPhraseSplitter.SplitItem::category)
                .containsExactly(tuple("leather jacket", GenericItemCategory.OUTERWEAR));
    }

    @Test
    void splitText_ampersandSeparator_splitsIntoDistinctItems() {
        List<RequestedItemPhraseSplitter.SplitItem> items =
                RequestedItemPhraseSplitter.splitText("shirt & trousers & shoes", null);

        assertThat(items).extracting(RequestedItemPhraseSplitter.SplitItem::category)
                .containsExactly(GenericItemCategory.TOP, GenericItemCategory.BOTTOM, GenericItemCategory.FOOTWEAR);
    }

    @Test
    void splitText_lineBreakSeparator_splitsIntoDistinctItems() {
        List<RequestedItemPhraseSplitter.SplitItem> items =
                RequestedItemPhraseSplitter.splitText("shirt\ntrousers\nshoes", null);

        assertThat(items).extracting(RequestedItemPhraseSplitter.SplitItem::category)
                .containsExactly(GenericItemCategory.TOP, GenericItemCategory.BOTTOM, GenericItemCategory.FOOTWEAR);
    }

    @Test
    void splitText_semicolonSeparator_splitsIntoDistinctItems() {
        List<RequestedItemPhraseSplitter.SplitItem> items =
                RequestedItemPhraseSplitter.splitText("shirt; trousers; shoes", null);

        assertThat(items).extracting(RequestedItemPhraseSplitter.SplitItem::category)
                .containsExactly(GenericItemCategory.TOP, GenericItemCategory.BOTTOM, GenericItemCategory.FOOTWEAR);
    }

    @Test
    void splitText_withNoRecognizedGarmentKeyword_returnsEmptyList() {
        assertThat(RequestedItemPhraseSplitter.splitText("something comfortable and stylish", null)).isEmpty();
    }

    @Test
    void splitText_blank_returnsEmptyList() {
        assertThat(RequestedItemPhraseSplitter.splitText("", null)).isEmpty();
    }

    @Test
    void splitPhrase_singleRecognizedPhrase_returnsExactlyOneItem() {
        assertThat(RequestedItemPhraseSplitter.splitPhrase("football boots", null)).hasSize(1);
    }

    @Test
    void splitPhrase_mergedMultiGarmentPhrase_splitsTheSameWayAsSplitText() {
        List<RequestedItemPhraseSplitter.SplitItem> items =
                RequestedItemPhraseSplitter.splitPhrase("shirt trousers shoes", null);

        assertThat(items).extracting(RequestedItemPhraseSplitter.SplitItem::category)
                .containsExactly(GenericItemCategory.TOP, GenericItemCategory.BOTTOM, GenericItemCategory.FOOTWEAR);
    }
}
