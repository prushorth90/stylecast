package com.stylecast.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Stock quantity for one {@link ProductVariant} at one fulfillment location.
 *
 * A variant may have more than one {@code InventoryRecord} (one per
 * location); {@link ProductVariant#isInStock()} sums across all of them.
 */
@Entity
@Table(name = "inventory_records")
public class InventoryRecord {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_variant_id", nullable = false)
    private ProductVariant productVariant;

    @Column(nullable = false, length = 60)
    private String location;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected InventoryRecord() {
        // JPA
    }

    public InventoryRecord(UUID id, ProductVariant productVariant, String location, int quantity, Instant updatedAt) {
        if (quantity < 0) {
            throw new IllegalArgumentException("quantity must not be negative");
        }
        this.id = id;
        this.productVariant = productVariant;
        this.location = location;
        this.quantity = quantity;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProductVariantId() {
        return productVariant.getId();
    }

    public String getLocation() {
        return location;
    }

    public int getQuantity() {
        return quantity;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
