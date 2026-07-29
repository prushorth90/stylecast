package com.stylecast.occasion;

import com.stylecast.catalog.ProductCategory;
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
 * The current occasion interpretation for a single event.
 *
 * <p>There is exactly one row per event, enforced by the unique constraint
 * on {@code event_id}: an automatic {@code GET} creates this row the first
 * time and reuses it afterward, while regeneration overwrites the same row
 * (new {@code generatedAt}, same {@code id}) rather than inserting a
 * duplicate. This entity is intentionally kept out of the public REST
 * contract; the controller and service layers always translate to/from
 * {@link com.stylecast.occasion.dto.OccasionInterpretationResponse}.
 *
 * <p>Category/requirement lists are persisted as {@code text[]} columns of
 * enum names (not native Postgres enum arrays), consistent with how
 * {@code EventStylePreferences} persists its color lists.
 */
@Entity
@Table(name = "event_occasion_interpretations")
public class OccasionInterpretation {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OccasionType occasion;

    @Enumerated(EnumType.STRING)
    @Column(name = "dress_code", nullable = false, length = 30)
    private InterpretedDressCode dressCode;

    @Column(name = "formality_level", nullable = false)
    private int formalityLevel;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "required_categories", nullable = false, columnDefinition = "text[]")
    private List<String> requiredCategories;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "optional_categories", nullable = false, columnDefinition = "text[]")
    private List<String> optionalCategories;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "preferred_colors", nullable = false, columnDefinition = "text[]")
    private List<String> preferredColors;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "colors_to_avoid", nullable = false, columnDefinition = "text[]")
    private List<String> colorsToAvoid;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "special_requirements", nullable = false, columnDefinition = "text[]")
    private List<String> specialRequirements;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private List<String> assumptions;

    @Column(nullable = false, precision = 3, scale = 2)
    private BigDecimal confidence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private InterpretationSource source;

    // Optional AI model identifier (e.g. "gpt-4.1"); null for a rule-based fallback
    // result. Never stores an API key, header, or raw provider response.
    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OccasionInterpretation() {
        // JPA
    }

    public OccasionInterpretation(UUID id, UUID eventId, Instant createdAt) {
        this.id = id;
        this.eventId = eventId;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    /**
     * Applies a freshly classified result to this row, whether it is being
     * created for the first time or regenerated.
     */
    public void apply(OccasionClassificationResult result, Instant now) {
        this.occasion = result.occasion();
        this.dressCode = result.dressCode();
        this.formalityLevel = result.formalityLevel();
        this.requiredCategories = toNames(result.requiredCategories());
        this.optionalCategories = toNames(result.optionalCategories());
        this.preferredColors = List.copyOf(result.preferredColors());
        this.colorsToAvoid = List.copyOf(result.colorsToAvoid());
        this.specialRequirements = toNames(result.specialRequirements());
        this.assumptions = List.copyOf(result.assumptions());
        this.confidence = result.confidence();
        this.source = result.source();
        this.modelName = result.modelName();
        this.generatedAt = now;
        this.updatedAt = now;
    }

    private static List<String> toNames(List<? extends Enum<?>> values) {
        return values.stream().map(Enum::name).toList();
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public OccasionType getOccasion() {
        return occasion;
    }

    public InterpretedDressCode getDressCode() {
        return dressCode;
    }

    public int getFormalityLevel() {
        return formalityLevel;
    }

    public List<ProductCategory> getRequiredCategories() {
        return requiredCategories.stream().map(ProductCategory::valueOf).toList();
    }

    public List<ProductCategory> getOptionalCategories() {
        return optionalCategories.stream().map(ProductCategory::valueOf).toList();
    }

    public List<String> getPreferredColors() {
        return preferredColors;
    }

    public List<String> getColorsToAvoid() {
        return colorsToAvoid;
    }

    public List<SpecialRequirement> getSpecialRequirements() {
        return specialRequirements.stream().map(SpecialRequirement::valueOf).toList();
    }

    public List<String> getAssumptions() {
        return assumptions;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public InterpretationSource getSource() {
        return source;
    }

    public String getModelName() {
        return modelName;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
