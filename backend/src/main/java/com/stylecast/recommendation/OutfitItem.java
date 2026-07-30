package com.stylecast.recommendation;

import com.stylecast.catalog.ProductCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One catalog product+variant selected for an {@link OutfitRecommendation}.
 *
 * <p>{@code productId}/{@code productVariantId} are plain foreign-key UUIDs
 * (not JPA associations) so this module stays a read-mostly consumer of
 * {@code com.stylecast.catalog} - product details (brand, name, color,
 * size, image) are looked up by id when building the response DTO, never
 * duplicated or invented here. {@code itemPrice} is always a snapshot of
 * {@link com.stylecast.catalog.ProductVariant#getEffectivePrice()} at
 * generation time.
 */
@Entity
@Table(name = "outfit_items")
public class OutfitItem {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recommendation_id", nullable = false)
    private OutfitRecommendation recommendation;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "product_variant_id", nullable = false)
    private UUID productVariantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductCategory category;

    @Column(name = "item_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal itemPrice;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OutfitItem() {
        // JPA
    }

    public OutfitItem(
            UUID id, UUID productId, UUID productVariantId, ProductCategory category, BigDecimal itemPrice,
            int displayOrder, Instant createdAt) {
        this.id = id;
        this.productId = productId;
        this.productVariantId = productVariantId;
        this.category = category;
        this.itemPrice = itemPrice;
        this.displayOrder = displayOrder;
        this.createdAt = createdAt;
    }

    void assignTo(OutfitRecommendation recommendation) {
        this.recommendation = recommendation;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRecommendationId() {
        return recommendation.getId();
    }

    public UUID getProductId() {
        return productId;
    }

    public UUID getProductVariantId() {
        return productVariantId;
    }

    public ProductCategory getCategory() {
        return category;
    }

    public BigDecimal getItemPrice() {
        return itemPrice;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
