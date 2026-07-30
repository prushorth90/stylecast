package com.stylecast.recommendation;

import com.stylecast.catalog.ProductCategory;
import com.stylecast.catalog.ProductRepository;
import com.stylecast.event.styling.PreferredStyle;
import com.stylecast.occasion.OccasionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ProductEligibilityService}, in particular that
 * effective price always reflects {@code priceOverride} when present.
 */
@ExtendWith(MockitoExtension.class)
class ProductEligibilityServiceTest {

    @Mock
    private ProductRepository productRepository;

    private RecommendationContext context() {
        var event = RecommendationFixtures.event();
        var preferences = RecommendationFixtures.preferences(
                event.getId(), BigDecimal.valueOf(1000), "M", "9", PreferredStyle.CLASSIC, List.of(), List.of());
        var interpretation = RecommendationFixtures.interpretation(
                event.getId(), OccasionType.WEDDING, 8, List.of(ProductCategory.SUIT), List.of(), List.of());
        return RecommendationFixtures.context(event, preferences, interpretation, Optional.empty());
    }

    @Test
    void findEligible_usesPriceOverrideWhenPresentInsteadOfBasePrice() {
        var product = RecommendationFixtures.product(ProductCategory.SUIT, 8, BigDecimal.valueOf(400), true);
        RecommendationFixtures.variant(product, "M", "Navy", BigDecimal.valueOf(250), 5);

        when(productRepository.findByCategoryAndActiveTrue(ProductCategory.SUIT)).thenReturn(List.of(product));
        for (ProductCategory category : ProductCategory.values()) {
            if (category != ProductCategory.SUIT) {
                when(productRepository.findByCategoryAndActiveTrue(category)).thenReturn(List.of());
            }
        }

        var service = new ProductEligibilityService(productRepository, new HardConstraintValidator());
        var eligible = service.findEligible(context());

        assertThat(eligible.get(ProductCategory.SUIT)).hasSize(1);
        assertThat(eligible.get(ProductCategory.SUIT).get(0).effectivePrice()).isEqualByComparingTo(BigDecimal.valueOf(250));
    }

    @Test
    void findEligible_withNoPriceOverride_usesBasePrice() {
        var product = RecommendationFixtures.product(ProductCategory.SUIT, 8, BigDecimal.valueOf(400), true);
        RecommendationFixtures.variant(product, "M", "Navy", null, 5);

        when(productRepository.findByCategoryAndActiveTrue(ProductCategory.SUIT)).thenReturn(List.of(product));
        for (ProductCategory category : ProductCategory.values()) {
            if (category != ProductCategory.SUIT) {
                when(productRepository.findByCategoryAndActiveTrue(category)).thenReturn(List.of());
            }
        }

        var service = new ProductEligibilityService(productRepository, new HardConstraintValidator());
        var eligible = service.findEligible(context());

        assertThat(eligible.get(ProductCategory.SUIT).get(0).effectivePrice()).isEqualByComparingTo(BigDecimal.valueOf(400));
    }

    @Test
    void findEligible_returnsEveryCatalogCategoryEvenWhenEmpty() {
        for (ProductCategory category : ProductCategory.values()) {
            when(productRepository.findByCategoryAndActiveTrue(category)).thenReturn(List.of());
        }

        var service = new ProductEligibilityService(productRepository, new HardConstraintValidator());
        var eligible = service.findEligible(context());

        assertThat(eligible.keySet()).containsExactlyInAnyOrder(ProductCategory.values());
        assertThat(eligible.values()).allSatisfy(list -> assertThat(list).isEmpty());
    }
}
