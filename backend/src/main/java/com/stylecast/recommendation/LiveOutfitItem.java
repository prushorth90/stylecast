package com.stylecast.recommendation;

import com.stylecast.catalog.ProductCategory;
import com.stylecast.occasion.GenericItemCategory;
import com.stylecast.retail.CandidateAudience;
import com.stylecast.retail.Retailer;
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
import java.util.List;
import java.util.UUID;

/**
 * One live Nordstrom product candidate selected for a {@link LiveOutfitRecommendation}.
 *
 * <p>Only fields the {@code com.stylecast.retail} provider actually returned
 * (or that StyleCast itself supplied as a search input, like {@code
 * requestedSize}) are ever populated - {@code brand}/{@code price}/{@code
 * originalPrice}/{@code currency}/{@code imageUrl}/{@code color}/{@code
 * availableSizes}/{@code stockText} stay {@code null}/empty, and {@code
 * priceVerified}/{@code sizeVerified}/{@code availabilityVerified} stay
 * {@code false}, unless a {@code ProductDetailEnricher} independently
 * confirms them. Nothing here is ever invented to fill a gap; see Task 8's
 * "no silent fallback" requirement.
 *
 * <p>Exactly one of {@code category} (legacy required-categories pipeline)
 * or {@code requestedItemPhrase}/{@code requestedItemGenericCategory}
 * (explicit-item pipeline, Task 8.5) is populated, depending on which
 * pipeline produced this item - see {@code LiveRecommendationService}.
 */
@Entity
@Table(name = "live_outfit_items")
public class LiveOutfitItem {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recommendation_id", nullable = false)
    private LiveOutfitRecommendation recommendation;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ProductCategory category;

    @Column(name = "requested_item_id")
    private UUID requestedItemId;

    @Column(name = "requested_item_phrase", length = 200)
    private String requestedItemPhrase;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_item_generic_category", length = 20)
    private GenericItemCategory requestedItemGenericCategory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Retailer retailer;

    @Column(length = 300)
    private String title;

    @Column(length = 150)
    private String brand;

    @Column(name = "product_url", nullable = false, length = 1000)
    private String productUrl;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "original_price", precision = 10, scale = 2)
    private BigDecimal originalPrice;

    @Column(length = 10)
    private String currency;

    @Column(name = "price_verified", nullable = false)
    private boolean priceVerified;

    @Column(length = 50)
    private String color;

    @Column(name = "requested_size", length = 50)
    private String requestedSize;

    @Column(name = "available_sizes", length = 300)
    private String availableSizes;

    @Column(name = "size_verified", nullable = false)
    private boolean sizeVerified;

    @Column(name = "stock_text", length = 100)
    private String stockText;

    @Column(name = "availability_verified", nullable = false)
    private boolean availabilityVerified;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CandidateAudience audience;

    @Column(name = "source_citation", length = 300)
    private String sourceCitation;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LiveOutfitItem() {
        // JPA
    }

    /** Legacy required-categories pipeline constructor - {@code requestedItemId}/{@code requestedItemPhrase}/{@code requestedItemGenericCategory} stay {@code null}. */
    public LiveOutfitItem(
            UUID id, ProductCategory category, Retailer retailer, String title, String brand, String productUrl,
            String imageUrl, BigDecimal price, BigDecimal originalPrice, String currency, boolean priceVerified,
            String color, String requestedSize, List<String> availableSizes, boolean sizeVerified, String stockText,
            boolean availabilityVerified, CandidateAudience audience, String sourceCitation, int displayOrder,
            Instant createdAt) {
        this(id, category, null, null, null, retailer, title, brand, productUrl, imageUrl, price, originalPrice,
                currency, priceVerified, color, requestedSize, availableSizes, sizeVerified, stockText,
                availabilityVerified, audience, sourceCitation, displayOrder, createdAt);
    }

    /** Explicit-item pipeline constructor (Task 8.5) - {@code category} stays {@code null}. */
    public LiveOutfitItem(
            UUID id, UUID requestedItemId, String requestedItemPhrase, GenericItemCategory requestedItemGenericCategory,
            Retailer retailer, String title, String brand, String productUrl,
            String imageUrl, BigDecimal price, BigDecimal originalPrice, String currency, boolean priceVerified,
            String color, String requestedSize, List<String> availableSizes, boolean sizeVerified, String stockText,
            boolean availabilityVerified, CandidateAudience audience, String sourceCitation, int displayOrder,
            Instant createdAt) {
        this(id, null, requestedItemId, requestedItemPhrase, requestedItemGenericCategory, retailer, title, brand,
                productUrl, imageUrl, price, originalPrice, currency, priceVerified, color, requestedSize,
                availableSizes, sizeVerified, stockText, availabilityVerified, audience, sourceCitation, displayOrder,
                createdAt);
    }

    private LiveOutfitItem(
            UUID id, ProductCategory category, UUID requestedItemId, String requestedItemPhrase,
            GenericItemCategory requestedItemGenericCategory, Retailer retailer, String title, String brand,
            String productUrl, String imageUrl, BigDecimal price, BigDecimal originalPrice, String currency,
            boolean priceVerified, String color, String requestedSize, List<String> availableSizes,
            boolean sizeVerified, String stockText, boolean availabilityVerified, CandidateAudience audience,
            String sourceCitation, int displayOrder, Instant createdAt) {
        this.id = id;
        this.category = category;
        this.requestedItemId = requestedItemId;
        this.requestedItemPhrase = requestedItemPhrase;
        this.requestedItemGenericCategory = requestedItemGenericCategory;
        this.retailer = retailer;
        this.title = title;
        this.brand = brand;
        this.productUrl = productUrl;
        this.imageUrl = imageUrl;
        this.price = price;
        this.originalPrice = originalPrice;
        this.currency = currency;
        this.priceVerified = priceVerified;
        this.color = color;
        this.requestedSize = requestedSize;
        this.availableSizes = (availableSizes == null || availableSizes.isEmpty()) ? null : String.join(",", availableSizes);
        this.sizeVerified = sizeVerified;
        this.stockText = stockText;
        this.availabilityVerified = availabilityVerified;
        this.audience = audience == null ? CandidateAudience.UNKNOWN : audience;
        this.sourceCitation = sourceCitation;
        this.displayOrder = displayOrder;
        this.createdAt = createdAt;
    }

    void assignTo(LiveOutfitRecommendation recommendation) {
        this.recommendation = recommendation;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRecommendationId() {
        return recommendation.getId();
    }

    public ProductCategory getCategory() {
        return category;
    }

    public UUID getRequestedItemId() {
        return requestedItemId;
    }

    public String getRequestedItemPhrase() {
        return requestedItemPhrase;
    }

    public GenericItemCategory getRequestedItemGenericCategory() {
        return requestedItemGenericCategory;
    }

    public Retailer getRetailer() {
        return retailer;
    }

    public String getTitle() {
        return title;
    }

    public String getBrand() {
        return brand;
    }

    public String getProductUrl() {
        return productUrl;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getOriginalPrice() {
        return originalPrice;
    }

    public String getCurrency() {
        return currency;
    }

    public boolean isPriceVerified() {
        return priceVerified;
    }

    public String getColor() {
        return color;
    }

    public String getRequestedSize() {
        return requestedSize;
    }

    public List<String> getAvailableSizes() {
        return (availableSizes == null || availableSizes.isBlank())
                ? List.of()
                : List.of(availableSizes.split(","));
    }

    public boolean isSizeVerified() {
        return sizeVerified;
    }

    public String getStockText() {
        return stockText;
    }

    public boolean isAvailabilityVerified() {
        return availabilityVerified;
    }

    public CandidateAudience getAudience() {
        return audience;
    }

    public String getSourceCitation() {
        return sourceCitation;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
