package com.stylecast.recommendation;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One generated, deterministically-scored outfit recommendation for an
 * event. Up to three exist per generation (see {@link #generation}); a
 * regeneration marks the previous generation's rows {@link
 * RecommendationStatus#SUPERSEDED} and inserts a new generation's rows
 * rather than deleting history.
 *
 * <p>When a generation finds no valid outfit, exactly one row is persisted
 * with {@link RecommendationStatus#NO_VALID_OUTFIT} (no items, {@code
 * noResultReason} explaining why) so a later {@code GET} can distinguish
 * "generated, found nothing valid" from "never generated" without
 * re-running generation.
 *
 * <p>This entity is intentionally kept out of the public REST contract; the
 * controller and service layers always translate to/from {@link
 * com.stylecast.recommendation.dto.OutfitRecommendationResponse}.
 */
@Entity
@Table(name = "outfit_recommendations")
public class OutfitRecommendation {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(nullable = false)
    private int generation;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecommendationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecommendationSource source;

    @Column(name = "rank_position")
    private Integer rankPosition;

    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "occasion_fit_score", nullable = false)
    private int occasionFitScore;

    @Column(name = "weather_fit_score", nullable = false)
    private int weatherFitScore;

    @Column(name = "style_fit_score", nullable = false)
    private int styleFitScore;

    @Column(name = "color_fit_score", nullable = false)
    private int colorFitScore;

    @Column(name = "budget_efficiency_score", nullable = false)
    private int budgetEfficiencyScore;

    @Column(name = "completeness_score", nullable = false)
    private int completenessScore;

    @Column(name = "overall_score", nullable = false)
    private int overallScore;

    @Column(name = "no_result_reason", length = 500)
    private String noResultReason;

    @Column(length = 500)
    private String explanation;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "recommendation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    @BatchSize(size = 20)
    private List<OutfitItem> items = new ArrayList<>();

    protected OutfitRecommendation() {
        // JPA
    }

    private OutfitRecommendation(UUID id, UUID eventId, int generation, Instant now) {
        this.id = id;
        this.eventId = eventId;
        this.generation = generation;
        this.source = RecommendationSource.LOCAL_CATALOG;
        this.totalPrice = BigDecimal.ZERO;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Creates an {@link RecommendationStatus#ACTIVE} row for a real generated outfit. */
    public static OutfitRecommendation active(
            UUID eventId, int generation, int rankPosition, String name, String explanation, OutfitScore score,
            BigDecimal totalPrice, Instant now) {
        OutfitRecommendation recommendation = new OutfitRecommendation(UUID.randomUUID(), eventId, generation, now);
        recommendation.status = RecommendationStatus.ACTIVE;
        recommendation.rankPosition = rankPosition;
        recommendation.name = name;
        recommendation.explanation = explanation;
        recommendation.totalPrice = totalPrice;
        recommendation.occasionFitScore = score.occasionFitScore();
        recommendation.weatherFitScore = score.weatherFitScore();
        recommendation.styleFitScore = score.styleFitScore();
        recommendation.colorFitScore = score.colorFitScore();
        recommendation.budgetEfficiencyScore = score.budgetEfficiencyScore();
        recommendation.completenessScore = score.completenessScore();
        recommendation.overallScore = score.overallScore();
        recommendation.generatedAt = now;
        return recommendation;
    }

    /** Creates the single {@link RecommendationStatus#NO_VALID_OUTFIT} placeholder row for a generation. */
    public static OutfitRecommendation noValidOutfit(UUID eventId, int generation, String reason, Instant now) {
        OutfitRecommendation recommendation = new OutfitRecommendation(UUID.randomUUID(), eventId, generation, now);
        recommendation.status = RecommendationStatus.NO_VALID_OUTFIT;
        recommendation.name = "No valid outfit found";
        recommendation.noResultReason = reason;
        recommendation.generatedAt = now;
        return recommendation;
    }

    public void addItem(OutfitItem item) {
        items.add(item);
        item.assignTo(this);
    }

    public void supersede(Instant now) {
        this.status = RecommendationStatus.SUPERSEDED;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public int getGeneration() {
        return generation;
    }

    public String getName() {
        return name;
    }

    public RecommendationStatus getStatus() {
        return status;
    }

    public RecommendationSource getSource() {
        return source;
    }

    public Integer getRankPosition() {
        return rankPosition;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public int getOccasionFitScore() {
        return occasionFitScore;
    }

    public int getWeatherFitScore() {
        return weatherFitScore;
    }

    public int getStyleFitScore() {
        return styleFitScore;
    }

    public int getColorFitScore() {
        return colorFitScore;
    }

    public int getBudgetEfficiencyScore() {
        return budgetEfficiencyScore;
    }

    public int getCompletenessScore() {
        return completenessScore;
    }

    public int getOverallScore() {
        return overallScore;
    }

    public String getNoResultReason() {
        return noResultReason;
    }

    public String getExplanation() {
        return explanation;
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

    public List<OutfitItem> getItems() {
        return items;
    }
}
