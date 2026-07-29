package com.stylecast.catalog;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A fictional catalog product (a specific design sold in one or more
 * {@link ProductVariant} size/color combinations).
 *
 * This entity is intentionally kept out of the public REST contract; the
 * controller and service layers always translate to/from {@link
 * com.stylecast.catalog.dto.ProductSummaryResponse} and {@link
 * com.stylecast.catalog.dto.ProductDetailResponse}.
 */
@Entity
@Table(name = "products")
public class Product {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String brand;

    @Column(nullable = false, length = 200)
    private String name;

    @Column
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductCategory category;

    @Column(name = "base_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal basePrice;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "formality_level", nullable = false)
    private int formalityLevel;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "product_occasion_tags", joinColumns = @JoinColumn(name = "product_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "occasion_tag")
    @BatchSize(size = 50)
    private Set<OccasionTag> occasionTags = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "product_style_tags", joinColumns = @JoinColumn(name = "product_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "style_tag")
    @BatchSize(size = 50)
    private Set<StyleTag> styleTags = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "product_weather_tags", joinColumns = @JoinColumn(name = "product_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "weather_tag")
    @BatchSize(size = 50)
    private Set<WeatherTag> weatherTags = new LinkedHashSet<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("clothingSize ASC, color ASC")
    @BatchSize(size = 50)
    private List<ProductVariant> variants = new ArrayList<>();

    protected Product() {
        // JPA
    }

    public Product(
            UUID id,
            String brand,
            String name,
            String description,
            ProductCategory category,
            BigDecimal basePrice,
            String imageUrl,
            int formalityLevel,
            boolean active,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.brand = brand;
        this.name = name;
        this.description = description;
        this.category = category;
        this.basePrice = basePrice;
        this.imageUrl = imageUrl;
        this.formalityLevel = formalityLevel;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getBrand() {
        return brand;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public ProductCategory getCategory() {
        return category;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public int getFormalityLevel() {
        return formalityLevel;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Set<OccasionTag> getOccasionTags() {
        return occasionTags;
    }

    public Set<StyleTag> getStyleTags() {
        return styleTags;
    }

    public Set<WeatherTag> getWeatherTags() {
        return weatherTags;
    }

    public List<ProductVariant> getVariants() {
        return variants;
    }
}
