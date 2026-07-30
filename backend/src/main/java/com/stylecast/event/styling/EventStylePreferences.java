package com.stylecast.event.styling;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A user's saved styling preferences for a single event.
 *
 * There is exactly one row per event, enforced by the unique constraint on
 * {@code event_id}. This entity is intentionally kept out of the public
 * REST contract; the controller and service layers always translate to/from
 * {@link com.stylecast.event.styling.dto.EventStylePreferencesResponse}.
 */
@Entity
@Table(name = "event_style_preferences")
public class EventStylePreferences {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "outfit_request", nullable = false, length = 2000)
    private String outfitRequest;

    @Column(name = "max_budget", nullable = false, precision = 10, scale = 2)
    private BigDecimal maxBudget;

    @Column(name = "clothing_size", nullable = false, length = 50)
    private String clothingSize;

    @Column(name = "shoe_size", nullable = false, length = 20)
    private String shoeSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_style", nullable = false, length = 20)
    private PreferredStyle preferredStyle;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "preferred_colors", nullable = false, columnDefinition = "text[]")
    private List<String> preferredColors;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "colors_to_avoid", nullable = false, columnDefinition = "text[]")
    private List<String> colorsToAvoid;

    @Enumerated(EnumType.STRING)
    @Column(name = "shopping_department", nullable = false, length = 20)
    private ShoppingDepartment shoppingDepartment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EventStylePreferences() {
        // JPA
    }

    public EventStylePreferences(UUID id, UUID eventId, Instant createdAt) {
        this.id = id;
        this.eventId = eventId;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    /**
     * Applies new preference values, trimming free-text fields and
     * normalizing the color lists (trimmed, blank entries removed).
     */
    public void apply(
            String outfitRequest,
            BigDecimal maxBudget,
            String clothingSize,
            String shoeSize,
            PreferredStyle preferredStyle,
            List<String> preferredColors,
            List<String> colorsToAvoid,
            ShoppingDepartment shoppingDepartment,
            Instant updatedAt) {
        this.outfitRequest = outfitRequest.trim();
        this.maxBudget = maxBudget;
        this.clothingSize = clothingSize.trim();
        this.shoeSize = shoeSize.trim();
        this.preferredStyle = preferredStyle;
        this.preferredColors = normalizeColors(preferredColors);
        this.colorsToAvoid = normalizeColors(colorsToAvoid);
        this.shoppingDepartment = shoppingDepartment == null ? ShoppingDepartment.NO_PREFERENCE : shoppingDepartment;
        this.updatedAt = updatedAt;
    }

    /**
     * Backward-compatible overload defaulting {@code shoppingDepartment} to
     * {@link ShoppingDepartment#NO_PREFERENCE}, for callers that predate
     * this field.
     */
    public void apply(
            String outfitRequest,
            BigDecimal maxBudget,
            String clothingSize,
            String shoeSize,
            PreferredStyle preferredStyle,
            List<String> preferredColors,
            List<String> colorsToAvoid,
            Instant updatedAt) {
        apply(outfitRequest, maxBudget, clothingSize, shoeSize, preferredStyle, preferredColors, colorsToAvoid,
                ShoppingDepartment.NO_PREFERENCE, updatedAt);
    }

    private static List<String> normalizeColors(List<String> colors) {
        if (colors == null) {
            return List.of();
        }
        return colors.stream()
                .filter(color -> color != null)
                .map(String::trim)
                .filter(color -> !color.isEmpty())
                .toList();
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getOutfitRequest() {
        return outfitRequest;
    }

    public BigDecimal getMaxBudget() {
        return maxBudget;
    }

    public String getClothingSize() {
        return clothingSize;
    }

    public String getShoeSize() {
        return shoeSize;
    }

    public PreferredStyle getPreferredStyle() {
        return preferredStyle;
    }

    public List<String> getPreferredColors() {
        return preferredColors;
    }

    public List<String> getColorsToAvoid() {
        return colorsToAvoid;
    }

    public ShoppingDepartment getShoppingDepartment() {
        return shoppingDepartment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
