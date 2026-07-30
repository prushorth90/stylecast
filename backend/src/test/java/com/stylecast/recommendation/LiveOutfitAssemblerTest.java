package com.stylecast.recommendation;

import com.stylecast.catalog.ProductCategory;
import com.stylecast.occasion.GenericItemCategory;
import com.stylecast.occasion.RequestedItem;
import com.stylecast.retail.CandidateAudience;
import com.stylecast.retail.RetailProductCandidate;
import com.stylecast.retail.RetailProductSource;
import com.stylecast.retail.Retailer;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LiveOutfitAssemblerTest {

    private final LiveOutfitAssembler assembler = new LiveOutfitAssembler();

    private RetailProductCandidate candidate(String url) {
        return new RetailProductCandidate(
                RetailProductSource.AI_WEB_SEARCH, Retailer.NORDSTROM, "Title for " + url, null, null, null, null,
                null, url, null, null, null, List.of(), null, false, false, false, CandidateAudience.UNKNOWN,
                Instant.now(), "fake");
    }

    private RequestedItem item(String phrase, GenericItemCategory category) {
        return new RequestedItem(UUID.randomUUID(), phrase, category, List.of(phrase), true, null, 0);
    }

    @Test
    void assemble_withEveryRequiredCategoryHavingThreeCandidates_returnsThreeDistinctOutfits() {
        Map<ProductCategory, List<RetailProductCandidate>> byCategory = new EnumMap<>(ProductCategory.class);
        byCategory.put(ProductCategory.SUIT, List.of(
                candidate("https://www.nordstrom.com/s/suit-1/1"),
                candidate("https://www.nordstrom.com/s/suit-2/2"),
                candidate("https://www.nordstrom.com/s/suit-3/3")));
        byCategory.put(ProductCategory.SHOES, List.of(
                candidate("https://www.nordstrom.com/s/shoes-1/1"),
                candidate("https://www.nordstrom.com/s/shoes-2/2"),
                candidate("https://www.nordstrom.com/s/shoes-3/3")));

        List<LiveOutfitAssembler.LiveAssembledOutfit> outfits =
                assembler.assemble(byCategory, List.of(ProductCategory.SUIT, ProductCategory.SHOES));

        assertThat(outfits).hasSize(3);
        assertThat(outfits).allSatisfy(outfit -> assertThat(outfit.items()).hasSize(2));
        // Every outfit must be a genuinely distinct pairing.
        List<String> keys = outfits.stream()
                .map(outfit -> outfit.items().stream().map(i -> i.candidate().productUrl()).sorted().toList().toString())
                .toList();
        assertThat(keys).doesNotHaveDuplicates();
    }

    @Test
    void assemble_withOneRequiredCategoryEmpty_assemblesPartialOutfitsFromFoundCategoriesOnly() {
        Map<ProductCategory, List<RetailProductCandidate>> byCategory = new EnumMap<>(ProductCategory.class);
        byCategory.put(ProductCategory.SUIT, List.of(candidate("https://www.nordstrom.com/s/suit-1/1")));
        byCategory.put(ProductCategory.SHOES, List.of());

        List<LiveOutfitAssembler.LiveAssembledOutfit> outfits =
                assembler.assemble(byCategory, List.of(ProductCategory.SUIT, ProductCategory.SHOES));

        assertThat(outfits).hasSize(1);
        assertThat(outfits.get(0).items()).hasSize(1);
        assertThat(outfits.get(0).items().get(0).category()).isEqualTo(ProductCategory.SUIT);
    }

    @Test
    void assemble_withEveryRequiredCategoryEmpty_returnsNoOutfits() {
        Map<ProductCategory, List<RetailProductCandidate>> byCategory = new EnumMap<>(ProductCategory.class);
        byCategory.put(ProductCategory.SUIT, List.of());
        byCategory.put(ProductCategory.SHOES, List.of());

        List<LiveOutfitAssembler.LiveAssembledOutfit> outfits =
                assembler.assemble(byCategory, List.of(ProductCategory.SUIT, ProductCategory.SHOES));

        assertThat(outfits).isEmpty();
    }

    @Test
    void foundCategories_reportsOnlyCategoriesWithAtLeastOneCandidate() {
        Map<ProductCategory, List<RetailProductCandidate>> byCategory = new EnumMap<>(ProductCategory.class);
        byCategory.put(ProductCategory.SUIT, List.of(candidate("https://www.nordstrom.com/s/suit-1/1")));
        byCategory.put(ProductCategory.SHOES, List.of());

        List<ProductCategory> found =
                assembler.foundCategories(byCategory, List.of(ProductCategory.SUIT, ProductCategory.SHOES));

        assertThat(found).containsExactly(ProductCategory.SUIT);
    }

    @Test
    void categoriesWithNoCandidates_reportsOnlyTheEmptyRequiredCategories() {
        Map<ProductCategory, List<RetailProductCandidate>> byCategory = new EnumMap<>(ProductCategory.class);
        byCategory.put(ProductCategory.SUIT, List.of(candidate("https://www.nordstrom.com/s/suit-1/1")));
        byCategory.put(ProductCategory.SHOES, List.of());

        List<ProductCategory> empty =
                assembler.categoriesWithNoCandidates(byCategory, List.of(ProductCategory.SUIT, ProductCategory.SHOES));

        assertThat(empty).containsExactly(ProductCategory.SHOES);
    }

    @Test
    void assemble_withOnlyOneCandidateInACategory_reusesItAcrossEveryOutfit() {
        Map<ProductCategory, List<RetailProductCandidate>> byCategory = new EnumMap<>(ProductCategory.class);
        byCategory.put(ProductCategory.SUIT, List.of(candidate("https://www.nordstrom.com/s/suit-1/1")));
        byCategory.put(ProductCategory.SHOES, List.of(
                candidate("https://www.nordstrom.com/s/shoes-1/1"),
                candidate("https://www.nordstrom.com/s/shoes-2/2"),
                candidate("https://www.nordstrom.com/s/shoes-3/3")));

        List<LiveOutfitAssembler.LiveAssembledOutfit> outfits =
                assembler.assemble(byCategory, List.of(ProductCategory.SUIT, ProductCategory.SHOES));

        assertThat(outfits).hasSize(3);
        assertThat(outfits).allSatisfy(outfit -> assertThat(outfit.items())
                .filteredOn(item -> item.category() == ProductCategory.SUIT)
                .allSatisfy(item -> assertThat(item.candidate().productUrl()).isEqualTo("https://www.nordstrom.com/s/suit-1/1")));
    }

    // --- Item-based assembly (Task 8.5) ---

    @Test
    void assembleFromItems_withEveryItemHavingACandidate_returnsAnOutfitWithAllItems() {
        RequestedItem jersey = item("USA soccer jersey", GenericItemCategory.TOP);
        RequestedItem shorts = item("soccer shorts", GenericItemCategory.BOTTOM);
        Map<UUID, List<RetailProductCandidate>> byItemId = new LinkedHashMap<>();
        byItemId.put(jersey.id(), List.of(candidate("https://www.nordstrom.com/s/jersey/1")));
        byItemId.put(shorts.id(), List.of(candidate("https://www.nordstrom.com/s/shorts/2")));

        List<LiveOutfitAssembler.LiveAssembledOutfit> outfits =
                assembler.assembleFromItems(byItemId, List.of(jersey, shorts));

        assertThat(outfits).hasSize(1);
        assertThat(outfits.get(0).items()).hasSize(2);
        assertThat(outfits.get(0).items()).allSatisfy(selected -> assertThat(selected.requestedItem()).isNotNull());
    }

    @Test
    void assembleFromItems_withOneItemMissingCandidates_omitsItRatherThanSubstitutingAnUnrelatedProduct() {
        RequestedItem jersey = item("USA soccer jersey", GenericItemCategory.TOP);
        RequestedItem boots = item("football boots", GenericItemCategory.FOOTWEAR);
        Map<UUID, List<RetailProductCandidate>> byItemId = new LinkedHashMap<>();
        byItemId.put(jersey.id(), List.of(candidate("https://www.nordstrom.com/s/jersey/1")));
        byItemId.put(boots.id(), List.of());

        List<LiveOutfitAssembler.LiveAssembledOutfit> outfits =
                assembler.assembleFromItems(byItemId, List.of(jersey, boots));

        assertThat(outfits).hasSize(1);
        assertThat(outfits.get(0).items()).hasSize(1);
        assertThat(outfits.get(0).items().get(0).requestedItem()).isEqualTo(jersey);
    }

    @Test
    void assembleFromItems_withEveryItemMissingCandidates_returnsNoOutfits() {
        RequestedItem jersey = item("USA soccer jersey", GenericItemCategory.TOP);
        Map<UUID, List<RetailProductCandidate>> byItemId = new LinkedHashMap<>();
        byItemId.put(jersey.id(), List.of());

        List<LiveOutfitAssembler.LiveAssembledOutfit> outfits = assembler.assembleFromItems(byItemId, List.of(jersey));

        assertThat(outfits).isEmpty();
    }

    @Test
    void foundItems_and_itemsWithNoCandidates_partitionCorrectly() {
        RequestedItem jersey = item("USA soccer jersey", GenericItemCategory.TOP);
        RequestedItem boots = item("football boots", GenericItemCategory.FOOTWEAR);
        Map<UUID, List<RetailProductCandidate>> byItemId = new LinkedHashMap<>();
        byItemId.put(jersey.id(), List.of(candidate("https://www.nordstrom.com/s/jersey/1")));
        byItemId.put(boots.id(), List.of());

        assertThat(assembler.foundItems(byItemId, List.of(jersey, boots))).containsExactly(jersey);
        assertThat(assembler.itemsWithNoCandidates(byItemId, List.of(jersey, boots))).containsExactly(boots);
    }
}
