package com.stylecast.catalog.dto;

import com.stylecast.catalog.OccasionTag;
import com.stylecast.catalog.Product;
import com.stylecast.catalog.ProductCategory;
import com.stylecast.catalog.StyleTag;
import com.stylecast.catalog.WeatherTag;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Public API representation of a {@link Product} for the product detail
 * endpoint, including every variant.
 */
public record ProductDetailResponse(
        UUID id,
        String brand,
        String name,
        String description,
        ProductCategory category,
        BigDecimal basePrice,
        String imageUrl,
        int formalityLevel,
        Set<OccasionTag> occasionTags,
        Set<StyleTag> styleTags,
        Set<WeatherTag> weatherTags,
        List<ProductVariantResponse> variants,
        boolean inStock,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProductDetailResponse fromEntity(Product product) {
        List<ProductVariantResponse> variants = product.getVariants().stream()
                .map(ProductVariantResponse::fromEntity)
                .toList();

        boolean inStock = variants.stream().anyMatch(ProductVariantResponse::inStock);

        // Copy each tag set into a plain LinkedHashSet (rather than storing
        // the live Hibernate-managed collection) so it's fully read here,
        // inside the transactional service call, instead of lazily on
        // serialization - which happens later, after the session is closed.
        return new ProductDetailResponse(
                product.getId(),
                product.getBrand(),
                product.getName(),
                product.getDescription(),
                product.getCategory(),
                product.getBasePrice(),
                product.getImageUrl(),
                product.getFormalityLevel(),
                new LinkedHashSet<>(product.getOccasionTags()),
                new LinkedHashSet<>(product.getStyleTags()),
                new LinkedHashSet<>(product.getWeatherTags()),
                variants,
                inStock,
                product.getCreatedAt(),
                product.getUpdatedAt());
    }
}
