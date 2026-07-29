package com.stylecast.catalog;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Composable {@link Specification} predicates for filtering {@link
 * Product}. Kept as small, single-purpose methods so {@link CatalogService}
 * can combine only the filters actually supplied on a given request.
 *
 * {@code clothingSize}, {@code color}, and {@code inStock} are combined into
 * a single {@link #matchesVariant} method (rather than three independent
 * specifications) so all three conditions are required of the *same*
 * variant row - joining the {@code variants} collection independently per
 * filter would let each condition match a different variant and silently
 * turn an AND filter into something closer to an OR.
 */
final class ProductSpecifications {

    private ProductSpecifications() {
    }

    static Specification<Product> isActive() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }

    static Specification<Product> hasCategory(ProductCategory category) {
        return (root, query, cb) -> cb.equal(root.get("category"), category);
    }

    static Specification<Product> formalityAtLeast(int minimumFormality) {
        return (root, query, cb) -> cb.ge(root.get("formalityLevel"), minimumFormality);
    }

    static Specification<Product> formalityAtMost(int maximumFormality) {
        return (root, query, cb) -> cb.le(root.get("formalityLevel"), maximumFormality);
    }

    static Specification<Product> basePriceAtMost(BigDecimal maxPrice) {
        return (root, query, cb) -> cb.le(root.get("basePrice"), maxPrice);
    }

    static Specification<Product> hasOccasionTag(OccasionTag occasionTag) {
        return (root, query, cb) -> {
            query.distinct(true);
            Join<Product, OccasionTag> tags = root.join("occasionTags", JoinType.INNER);
            return cb.equal(tags, occasionTag);
        };
    }

    static Specification<Product> hasStyleTag(StyleTag styleTag) {
        return (root, query, cb) -> {
            query.distinct(true);
            Join<Product, StyleTag> tags = root.join("styleTags", JoinType.INNER);
            return cb.equal(tags, styleTag);
        };
    }

    static Specification<Product> hasWeatherTag(WeatherTag weatherTag) {
        return (root, query, cb) -> {
            query.distinct(true);
            Join<Product, WeatherTag> tags = root.join("weatherTags", JoinType.INNER);
            return cb.equal(tags, weatherTag);
        };
    }

    static Specification<Product> matchesVariant(String clothingSize, String color, Boolean inStock) {
        return (root, query, cb) -> {
            query.distinct(true);
            Join<Product, ProductVariant> variants = root.join("variants", JoinType.INNER);

            List<Predicate> predicates = new ArrayList<>();
            if (clothingSize != null) {
                predicates.add(cb.equal(cb.upper(variants.get("clothingSize")), clothingSize.toUpperCase()));
            }
            if (color != null) {
                predicates.add(cb.equal(cb.upper(variants.get("color")), color.toUpperCase()));
            }
            if (Boolean.TRUE.equals(inStock)) {
                Join<ProductVariant, InventoryRecord> inventory = variants.join("inventoryRecords", JoinType.INNER);
                predicates.add(cb.greaterThan(inventory.get("quantity"), 0));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
