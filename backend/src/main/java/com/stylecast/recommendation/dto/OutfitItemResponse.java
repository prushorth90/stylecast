package com.stylecast.recommendation.dto;

import com.stylecast.catalog.ProductCategory;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Public API representation of one {@link com.stylecast.recommendation.OutfitItem},
 * enriched with catalog display fields (brand/name/color/size/image) so the
 * frontend never needs a second round trip to the catalog API to render a
 * summary card.
 */
public record OutfitItemResponse(
        UUID id,
        UUID productId,
        UUID productVariantId,
        ProductCategory category,
        String brand,
        String name,
        String color,
        String size,
        BigDecimal itemPrice,
        int displayOrder,
        String imageUrl) {
}
