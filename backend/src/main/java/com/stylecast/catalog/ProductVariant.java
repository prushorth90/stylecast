package com.stylecast.catalog;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A specific size/color combination a {@link Product} is sold in.
 */
@Entity
@Table(name = "product_variants")
public class ProductVariant {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 64)
    private String sku;

    @Column(name = "clothing_size", nullable = false, length = 20)
    private String clothingSize;

    @Column(nullable = false, length = 40)
    private String color;

    @Column(name = "price_override", precision = 10, scale = 2)
    private BigDecimal priceOverride;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "productVariant", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    private List<InventoryRecord> inventoryRecords = new ArrayList<>();

    protected ProductVariant() {
        // JPA
    }

    public ProductVariant(
            UUID id,
            Product product,
            String sku,
            String clothingSize,
            String color,
            BigDecimal priceOverride,
            Instant createdAt) {
        this.id = id;
        this.product = product;
        this.sku = sku;
        this.clothingSize = clothingSize;
        this.color = color;
        this.priceOverride = priceOverride;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProductId() {
        return product.getId();
    }

    public Product getProduct() {
        return product;
    }

    public String getSku() {
        return sku;
    }

    public String getClothingSize() {
        return clothingSize;
    }

    public String getColor() {
        return color;
    }

    public BigDecimal getPriceOverride() {
        return priceOverride;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<InventoryRecord> getInventoryRecords() {
        return inventoryRecords;
    }

    /**
     * Effective selling price for this variant: {@link #priceOverride} when
     * set, otherwise the owning product's {@code basePrice}.
     */
    public BigDecimal getEffectivePrice() {
        return priceOverride != null ? priceOverride : product.getBasePrice();
    }

    /**
     * Total quantity available across all inventory locations.
     */
    public int getTotalQuantity() {
        return inventoryRecords.stream().mapToInt(InventoryRecord::getQuantity).sum();
    }

    public boolean isInStock() {
        return getTotalQuantity() > 0;
    }
}
