package com.stylecast.recommendation;

import com.stylecast.catalog.ProductCategory;
import com.stylecast.event.styling.PreferredStyle;
import com.stylecast.occasion.OccasionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OutfitCombinationGenerator}: budget enforcement,
 * no-duplicate-product guarantee, required-category completeness, and the
 * SUIT-vs-BLAZER+TROUSERS conditional slot coupling.
 */
class OutfitCombinationGeneratorTest {

    private final OutfitCombinationGenerator generator = new OutfitCombinationGenerator(new HardConstraintValidator());

    private RecommendationContext context(BigDecimal budget) {
        var event = RecommendationFixtures.event();
        var preferences = RecommendationFixtures.preferences(
                event.getId(), budget, "M", "9", PreferredStyle.CLASSIC, List.of(), List.of());
        var interpretation = RecommendationFixtures.interpretation(
                event.getId(), OccasionType.WEDDING, 8, List.of(ProductCategory.SUIT), List.of(ProductCategory.ACCESSORY), List.of());
        return RecommendationFixtures.context(event, preferences, interpretation, Optional.empty());
    }

    private EligibleCandidate candidateFor(ProductCategory category, BigDecimal price, String size) {
        var product = RecommendationFixtures.product(category, 8, price, true);
        var variant = RecommendationFixtures.variant(product, size, "Navy", null, 5);
        return new EligibleCandidate(product, variant, variant.getEffectivePrice());
    }

    private Map<ProductCategory, List<EligibleCandidate>> catalogWithFormalMenswearOptions(BigDecimal suitPrice, BigDecimal blazerPrice) {
        Map<ProductCategory, List<EligibleCandidate>> map = new EnumMap<>(ProductCategory.class);
        map.put(ProductCategory.SUIT, List.of(candidateFor(ProductCategory.SUIT, suitPrice, "M")));
        map.put(ProductCategory.BLAZER, List.of(candidateFor(ProductCategory.BLAZER, blazerPrice, "M")));
        map.put(ProductCategory.TROUSERS, List.of(candidateFor(ProductCategory.TROUSERS, BigDecimal.valueOf(80), "M")));
        map.put(ProductCategory.SHIRT, List.of(candidateFor(ProductCategory.SHIRT, BigDecimal.valueOf(60), "M")));
        map.put(ProductCategory.SHOES, List.of(candidateFor(ProductCategory.SHOES, BigDecimal.valueOf(150), "9")));
        map.put(ProductCategory.ACCESSORY, List.of(candidateFor(ProductCategory.ACCESSORY, BigDecimal.valueOf(40), "ONE_SIZE")));
        map.put(ProductCategory.OUTERWEAR, List.of());
        return map;
    }

    @Test
    void generate_neverExceedsBudget() {
        var context = context(BigDecimal.valueOf(500));
        var eligible = catalogWithFormalMenswearOptions(BigDecimal.valueOf(400), BigDecimal.valueOf(180));

        List<OutfitCandidate> combos = generator.generate(OutfitTemplateCatalog.FORMAL_MENSWEAR, eligible, context);

        assertThat(combos).isNotEmpty();
        assertThat(combos).allSatisfy(combo -> assertThat(combo.totalPrice()).isLessThanOrEqualTo(context.maxBudget()));
    }

    @Test
    void generate_neverSelectsTheSameProductTwice() {
        var context = context(BigDecimal.valueOf(2000));
        var eligible = catalogWithFormalMenswearOptions(BigDecimal.valueOf(400), BigDecimal.valueOf(180));

        List<OutfitCandidate> combos = generator.generate(OutfitTemplateCatalog.FORMAL_MENSWEAR, eligible, context);

        assertThat(combos).isNotEmpty();
        for (OutfitCandidate combo : combos) {
            long distinctProducts = combo.items().stream().map(item -> item.candidate().product().getId()).distinct().count();
            assertThat(distinctProducts).isEqualTo(combo.items().size());
        }
    }

    @Test
    void generate_everyComboSatisfiesRequiredCategories() {
        var context = context(BigDecimal.valueOf(2000));
        var eligible = catalogWithFormalMenswearOptions(BigDecimal.valueOf(400), BigDecimal.valueOf(180));

        List<OutfitCandidate> combos = generator.generate(OutfitTemplateCatalog.FORMAL_MENSWEAR, eligible, context);

        assertThat(combos).isNotEmpty();
        for (OutfitCandidate combo : combos) {
            var categories = combo.items().stream().map(SelectedItem::category).toList();
            assertThat(categories).contains(ProductCategory.SHOES);
            assertThat(categories).anyMatch(category -> category == ProductCategory.SUIT || category == ProductCategory.BLAZER);
        }
    }

    @Test
    void generate_whenSuitChosen_doesNotAlsoRequireSeparateTrousers() {
        var context = context(BigDecimal.valueOf(2000));
        Map<ProductCategory, List<EligibleCandidate>> eligible = catalogWithFormalMenswearOptions(BigDecimal.valueOf(400), BigDecimal.valueOf(180));
        eligible.put(ProductCategory.TROUSERS, List.of()); // no trousers available at all

        List<OutfitCandidate> combos = generator.generate(OutfitTemplateCatalog.FORMAL_MENSWEAR, eligible, context);

        // A suit-based combo must still be produced even though no trousers exist,
        // because a SUIT already includes trousers.
        assertThat(combos).anyMatch(combo ->
                combo.items().stream().anyMatch(item -> item.category() == ProductCategory.SUIT));
    }

    @Test
    void generate_whenBlazerChosen_alsoRequiresTrousers() {
        var context = context(BigDecimal.valueOf(2000));
        Map<ProductCategory, List<EligibleCandidate>> eligible = catalogWithFormalMenswearOptions(BigDecimal.valueOf(400), BigDecimal.valueOf(180));
        eligible.put(ProductCategory.SUIT, List.of()); // force BLAZER path
        eligible.put(ProductCategory.TROUSERS, List.of()); // ...but no trousers available

        List<OutfitCandidate> combos = generator.generate(OutfitTemplateCatalog.FORMAL_MENSWEAR, eligible, context);

        // No valid combo can be built: BLAZER requires TROUSERS, and none exist.
        assertThat(combos).isEmpty();
    }

    @Test
    void generate_withNoEligibleCandidatesForARequiredCategory_returnsNoCombinations() {
        var context = context(BigDecimal.valueOf(2000));
        Map<ProductCategory, List<EligibleCandidate>> eligible = catalogWithFormalMenswearOptions(BigDecimal.valueOf(400), BigDecimal.valueOf(180));
        eligible.put(ProductCategory.SHOES, List.of());

        List<OutfitCandidate> combos = generator.generate(OutfitTemplateCatalog.FORMAL_MENSWEAR, eligible, context);

        assertThat(combos).isEmpty();
    }

    @Test
    void generate_withImpossibleBudget_returnsNoCombinations() {
        var context = context(BigDecimal.valueOf(10));
        var eligible = catalogWithFormalMenswearOptions(BigDecimal.valueOf(400), BigDecimal.valueOf(180));

        List<OutfitCandidate> combos = generator.generate(OutfitTemplateCatalog.FORMAL_MENSWEAR, eligible, context);

        assertThat(combos).isEmpty();
    }
}
