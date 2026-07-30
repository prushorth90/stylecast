package com.stylecast.recommendation;

import com.stylecast.catalog.ProductCategory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CategorySynonymsTest {

    @Test
    void synonymsFor_trousers_matchesTheSpecifiedList() {
        assertThat(CategorySynonyms.synonymsFor(ProductCategory.TROUSERS))
                .containsExactly("trousers", "dress pants", "pants", "chinos");
    }

    @Test
    void synonymsFor_shirt_matchesTheSpecifiedList() {
        assertThat(CategorySynonyms.synonymsFor(ProductCategory.SHIRT))
                .containsExactly("shirt", "dress shirt", "button-up shirt");
    }

    @Test
    void synonymsFor_shoes_matchesTheSpecifiedList() {
        assertThat(CategorySynonyms.synonymsFor(ProductCategory.SHOES))
                .containsExactly("dress shoes", "loafers", "oxfords");
    }

    @Test
    void synonymsFor_accessory_matchesTheSpecifiedList() {
        assertThat(CategorySynonyms.synonymsFor(ProductCategory.ACCESSORY))
                .containsExactly("tie", "belt", "pocket square");
    }

    @Test
    void synonymsFor_suit_matchesTheSpecifiedList() {
        assertThat(CategorySynonyms.synonymsFor(ProductCategory.SUIT))
                .containsExactly("suit", "tuxedo", "dinner jacket");
    }

    @Test
    void synonymsFor_categoryWithNoDefinedSynonyms_returnsEmptyList() {
        assertThat(CategorySynonyms.synonymsFor(ProductCategory.DRESS)).isEmpty();
        assertThat(CategorySynonyms.synonymsFor(ProductCategory.SKIRT)).isEmpty();
        assertThat(CategorySynonyms.synonymsFor(ProductCategory.BLAZER)).isEmpty();
    }
}
