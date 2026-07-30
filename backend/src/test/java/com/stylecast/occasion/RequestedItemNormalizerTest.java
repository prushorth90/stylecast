package com.stylecast.occasion;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RequestedItemNormalizerTest {

    @Test
    void normalize_withBlankOriginalPhrase_returnsNull() {
        assertThat(RequestedItemNormalizer.normalize("   ", GenericItemCategory.TOP, List.of("shirt"), true, null, 0))
                .isNull();
    }

    @Test
    void normalize_withNullOriginalPhrase_returnsNull() {
        assertThat(RequestedItemNormalizer.normalize(null, GenericItemCategory.TOP, List.of("shirt"), true, null, 0))
                .isNull();
    }

    @Test
    void normalize_withNullGenericCategory_returnsNull() {
        assertThat(RequestedItemNormalizer.normalize("USA soccer jersey", null, List.of("jersey"), true, null, 0))
                .isNull();
    }

    @Test
    void normalize_trimsOriginalPhrase() {
        RequestedItem item = RequestedItemNormalizer.normalize(
                "  USA soccer jersey  ", GenericItemCategory.TOP, List.of("jersey"), true, null, 0);

        assertThat(item).isNotNull();
        assertThat(item.originalPhrase()).isEqualTo("USA soccer jersey");
    }

    @Test
    void normalize_removesDuplicateSearchTermsCaseInsensitively() {
        RequestedItem item = RequestedItemNormalizer.normalize(
                "football boots", GenericItemCategory.FOOTWEAR,
                List.of("football boots", "Football Boots", "soccer cleats"), true, "soccer", 0);

        assertThat(item.searchTerms()).containsExactly("football boots", "soccer cleats");
    }

    @Test
    void normalize_limitsSearchTermsToMax() {
        List<String> manyTerms = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            manyTerms.add("term" + i);
        }

        RequestedItem item = RequestedItemNormalizer.normalize(
                "some item", GenericItemCategory.OTHER, manyTerms, true, null, 0);

        assertThat(item.searchTerms()).hasSize(RequestedItemNormalizer.MAX_SEARCH_TERMS);
    }

    @Test
    void normalize_withNoSearchTerms_fallsBackToOriginalPhrase() {
        RequestedItem item = RequestedItemNormalizer.normalize(
                "swim cap", GenericItemCategory.ACCESSORY, List.of(), true, "swimming", 0);

        assertThat(item.searchTerms()).containsExactly("swim cap");
    }

    @Test
    void normalize_withNullRequired_defaultsToTrue() {
        RequestedItem item = RequestedItemNormalizer.normalize(
                "tie", GenericItemCategory.ACCESSORY, List.of("tie"), null, null, 0);

        assertThat(item.required()).isTrue();
    }

    @Test
    void normalize_withBlankActivityContext_yieldsNull() {
        RequestedItem item = RequestedItemNormalizer.normalize(
                "tie", GenericItemCategory.ACCESSORY, List.of("tie"), true, "   ", 0);

        assertThat(item.activityContext()).isNull();
    }

    @Test
    void normalize_truncatesOverlyLongPhrase() {
        String longPhrase = "a".repeat(RequestedItemNormalizer.MAX_PHRASE_LENGTH + 50);

        RequestedItem item = RequestedItemNormalizer.normalize(
                longPhrase, GenericItemCategory.OTHER, List.of(), true, null, 0);

        assertThat(item.originalPhrase()).hasSize(RequestedItemNormalizer.MAX_PHRASE_LENGTH);
    }

    @Test
    void normalize_assignsAUniqueIdAndPreservesDisplayOrder() {
        RequestedItem first = RequestedItemNormalizer.normalize("tie", GenericItemCategory.ACCESSORY, List.of(), true, null, 0);
        RequestedItem second = RequestedItemNormalizer.normalize("belt", GenericItemCategory.ACCESSORY, List.of(), true, null, 1);

        assertThat(first.id()).isNotNull().isNotEqualTo(second.id());
        assertThat(first.displayOrder()).isZero();
        assertThat(second.displayOrder()).isEqualTo(1);
    }
}
