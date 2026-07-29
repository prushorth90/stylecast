package com.stylecast.catalog.dto;

import com.stylecast.catalog.ProductVariant;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Public API representation of a {@link ProductVariant}.
 */
public record ProductVariantResponse(
        UUID id,
        String sku,
        String clothingSize,
        String color,
        BigDecimal effectivePrice,
        int quantityAvailable,
        boolean inStock
) {
    public static ProductVariantResponse fromEntity(ProductVariant variant) {
        return new ProductVariantResponse(
                variant.getId(),
                variant.getSku(),
                variant.getClothingSize(),
                variant.getColor(),
                variant.getEffectivePrice(),
                variant.getTotalQuantity(),
                variant.isInStock());
    }
}
