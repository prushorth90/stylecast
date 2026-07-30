package com.stylecast.recommendation;

import com.stylecast.catalog.Product;
import com.stylecast.catalog.ProductCategory;
import com.stylecast.catalog.ProductRepository;
import com.stylecast.catalog.ProductVariant;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Reduces the local product catalog down to, per category, the products
 * (each paired with exactly one qualifying variant) that pass every
 * per-item hard constraint for a given event - active, correct size, in
 * stock, not an avoided color, suitable formality, and no direct weather
 * conflict. See {@link HardConstraintValidator#validateItem} for the exact
 * rules; this class only decides *which* product+variant pairs to keep, it
 * never re-implements a constraint itself.
 *
 * <p>A product can have several variants (colors); when more than one
 * variant of the same product passes every constraint, the lowest-id
 * variant is kept so results are deterministic and reproducible across
 * runs.
 */
@Service
class ProductEligibilityService {

    private final ProductRepository productRepository;
    private final HardConstraintValidator hardConstraintValidator;

    ProductEligibilityService(ProductRepository productRepository, HardConstraintValidator hardConstraintValidator) {
        this.productRepository = productRepository;
        this.hardConstraintValidator = hardConstraintValidator;
    }

    /**
     * Eligible candidates for every catalog category, keyed by category.
     * Categories with no eligible candidates are still present in the map
     * (with an empty list) so callers can distinguish "checked, found none"
     * from "never checked".
     */
    Map<ProductCategory, List<EligibleCandidate>> findEligible(RecommendationContext context) {
        Map<ProductCategory, List<EligibleCandidate>> byCategory = new EnumMap<>(ProductCategory.class);

        for (ProductCategory category : ProductCategory.values()) {
            byCategory.put(category, findEligibleForCategory(category, context));
        }

        return byCategory;
    }

    private List<EligibleCandidate> findEligibleForCategory(ProductCategory category, RecommendationContext context) {
        List<EligibleCandidate> candidates = new ArrayList<>();

        for (Product product : productRepository.findByCategoryAndActiveTrue(category)) {
            bestQualifyingVariant(category, product, context).ifPresent(variant ->
                    candidates.add(new EligibleCandidate(product, variant, variant.getEffectivePrice())));
        }

        candidates.sort(
                Comparator.comparingInt((EligibleCandidate c) -> Math.abs(c.product().getFormalityLevel() - context.formalityLevel()))
                        .thenComparing(c -> c.effectivePrice())
                        .thenComparing(c -> c.product().getId()));

        return candidates;
    }

    private java.util.Optional<ProductVariant> bestQualifyingVariant(ProductCategory category, Product product, RecommendationContext context) {
        return product.getVariants().stream()
                .filter(variant -> hardConstraintValidator.validateItem(category, product, variant, context).isEmpty())
                .min(Comparator.comparing(ProductVariant::getId));
    }
}
