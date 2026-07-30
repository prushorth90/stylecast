package com.stylecast.recommendation;

import com.stylecast.catalog.ProductCategory;
import com.stylecast.occasion.GenericItemCategory;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * One generated outfit recommendation for an event, assembled from live
 * {@code com.stylecast.retail} Nordstrom search candidates (Task 8) rather
 * than the local catalog.
 *
 * <p>Mirrors the versioning scheme already used by {@link OutfitRecommendation}
 * (Task 7A): up to three rows exist per {@code generation}; a regeneration
 * marks the previous generation's {@link RecommendationStatus#ACTIVE} rows
 * {@link RecommendationStatus#SUPERSEDED} rather than deleting them. Exactly
 * one {@link RecommendationStatus#NO_VALID_OUTFIT} row (no items) is
 * persisted when a generation attempt assembled no outfit at all (a
 * {@link LiveRecommendationCompleteness#NO_RESULTS} or {@link
 * LiveRecommendationCompleteness#PROVIDER_UNAVAILABLE} outcome).
 *
 * <p>{@code completeness}/{@code foundCategories}/{@code missingCategories}/
 * {@code message} are per-generation facts (identical across every row of
 * the same generation, denormalized the same way {@code explanation}
 * already was) - see {@link LiveRecommendationService} for how each
 * required category is searched independently so a search that finds only
 * some categories still returns valid candidates for those, instead of
 * discarding the whole attempt.
 *
 * <p>Kept as a separate table/entity from {@link OutfitRecommendation}
 * rather than reused, because live items reference external Nordstrom
 * product pages (URL, unverified price/size) instead of local catalog
 * product/variant ids - the two shapes are not interchangeable.
 */
@Entity
@Table(name = "live_outfit_recommendations")
public class LiveOutfitRecommendation {

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LiveRecommendationCompleteness completeness;

    @Column(name = "found_categories", length = 300)
    private String foundCategories;

    @Column(name = "missing_categories", length = 300)
    private String missingCategories;

    @Column(name = "found_requested_items", columnDefinition = "text")
    private String foundRequestedItems;

    @Column(name = "missing_requested_items", columnDefinition = "text")
    private String missingRequestedItems;

    @Column(name = "rank_position")
    private Integer rankPosition;

    @Column(length = 500)
    private String message;

    @Column(length = 500)
    private String explanation;

    /**
     * {@code true} once the event's saved styling preferences changed in an
     * interpretation-relevant way (outfitRequest/preferredStyle/
     * preferredColors/colorsToAvoid) AFTER this row was generated - see
     * {@link LiveRecommendationService#invalidateStaleRecommendations}.
     * Never cleared automatically; only a fresh {@code generate}/{@code
     * retry-missing} call (which creates a brand-new generation) moves past
     * it. Marking a row stale never removes it and never triggers a live
     * search - it is a pure metadata flag for the frontend to show an
     * "outdated, regenerate for the latest looks" notice.
     */
    @Column(nullable = false)
    private boolean stale;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "recommendation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    @BatchSize(size = 20)
    private List<LiveOutfitItem> items = new ArrayList<>();

    protected LiveOutfitRecommendation() {
        // JPA
    }

    private LiveOutfitRecommendation(UUID id, UUID eventId, int generation, Instant now) {
        this.id = id;
        this.eventId = eventId;
        this.generation = generation;
        this.source = RecommendationSource.LIVE_NORDSTROM;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Creates an {@link RecommendationStatus#ACTIVE} row for a real assembled
     * (complete or partial) live outfit. Exactly one of {@code
     * foundCategories}/{@code missingCategories} or {@code
     * foundRequestedItems}/{@code missingRequestedItems} is non-empty,
     * depending on which pipeline produced this generation - the other pair
     * stays empty.
     */
    public static LiveOutfitRecommendation active(
            UUID eventId, int generation, int rankPosition, String name, String explanation,
            LiveRecommendationCompleteness completeness, List<ProductCategory> foundCategories,
            List<ProductCategory> missingCategories, List<RequestedItemSummary> foundRequestedItems,
            List<RequestedItemSummary> missingRequestedItems, String message, Instant now) {
        LiveOutfitRecommendation recommendation = new LiveOutfitRecommendation(UUID.randomUUID(), eventId, generation, now);
        recommendation.status = RecommendationStatus.ACTIVE;
        recommendation.rankPosition = rankPosition;
        recommendation.name = name;
        recommendation.explanation = explanation;
        recommendation.completeness = completeness;
        recommendation.foundCategories = joinCategories(foundCategories);
        recommendation.missingCategories = joinCategories(missingCategories);
        recommendation.foundRequestedItems = joinRequestedItems(foundRequestedItems);
        recommendation.missingRequestedItems = joinRequestedItems(missingRequestedItems);
        recommendation.message = message;
        recommendation.generatedAt = now;
        return recommendation;
    }

    /**
     * Creates the single {@link RecommendationStatus#NO_VALID_OUTFIT} placeholder
     * row for a generation that assembled no outfit at all ({@link
     * LiveRecommendationCompleteness#NO_RESULTS} or {@link
     * LiveRecommendationCompleteness#PROVIDER_UNAVAILABLE}).
     */
    public static LiveOutfitRecommendation withoutOutfit(
            UUID eventId, int generation, LiveRecommendationCompleteness completeness,
            List<ProductCategory> foundCategories, List<ProductCategory> missingCategories,
            List<RequestedItemSummary> foundRequestedItems, List<RequestedItemSummary> missingRequestedItems,
            String message, Instant now) {
        LiveOutfitRecommendation recommendation = new LiveOutfitRecommendation(UUID.randomUUID(), eventId, generation, now);
        recommendation.status = RecommendationStatus.NO_VALID_OUTFIT;
        recommendation.name = completeness == LiveRecommendationCompleteness.PROVIDER_UNAVAILABLE
                ? "Live search temporarily unavailable"
                : "No suitable products found";
        recommendation.completeness = completeness;
        recommendation.foundCategories = joinCategories(foundCategories);
        recommendation.missingCategories = joinCategories(missingCategories);
        recommendation.foundRequestedItems = joinRequestedItems(foundRequestedItems);
        recommendation.missingRequestedItems = joinRequestedItems(missingRequestedItems);
        recommendation.message = message;
        recommendation.generatedAt = now;
        return recommendation;
    }

    public void addItem(LiveOutfitItem item) {
        items.add(item);
        item.assignTo(this);
    }

    public void supersede(Instant now) {
        this.status = RecommendationStatus.SUPERSEDED;
        this.updatedAt = now;
    }

    public void markStale(Instant now) {
        this.stale = true;
        this.updatedAt = now;
    }

    public boolean isStale() {
        return stale;
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

    public LiveRecommendationCompleteness getCompleteness() {
        return completeness;
    }

    public List<ProductCategory> getFoundCategories() {
        return parseCategories(foundCategories);
    }

    public List<ProductCategory> getMissingCategories() {
        return parseCategories(missingCategories);
    }

    /** Never {@code null}; empty when this generation used the category-template pipeline instead. */
    public List<RequestedItemSummary> getFoundRequestedItems() {
        return parseRequestedItems(foundRequestedItems);
    }

    /** Never {@code null}; empty when this generation used the category-template pipeline instead. */
    public List<RequestedItemSummary> getMissingRequestedItems() {
        return parseRequestedItems(missingRequestedItems);
    }

    public Integer getRankPosition() {
        return rankPosition;
    }

    public String getMessage() {
        return message;
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

    private static String joinCategories(List<ProductCategory> categories) {
        return (categories == null || categories.isEmpty())
                ? null
                : categories.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    private static List<ProductCategory> parseCategories(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(",")).map(ProductCategory::valueOf).toList();
    }

    // Field separator / record separator (ASCII 0x1F/0x1E) - control characters that will
    // never appear in ordinary user-entered phrases, so no escaping is needed. Same
    // "accept the theoretical risk for a small denormalized display column" trade-off
    // already made by joinCategories' comma-joining above.
    private static final String ITEM_FIELD_SEPARATOR = "\u001F";
    private static final String ITEM_RECORD_SEPARATOR = "\u001E";

    private static String joinRequestedItems(List<RequestedItemSummary> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        return items.stream()
                .map(item -> String.join(ITEM_FIELD_SEPARATOR,
                        item.id().toString(),
                        item.originalPhrase(),
                        item.genericCategory().name(),
                        item.activityContext() == null ? "" : item.activityContext()))
                .collect(Collectors.joining(ITEM_RECORD_SEPARATOR));
    }

    private static List<RequestedItemSummary> parseRequestedItems(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<RequestedItemSummary> items = new ArrayList<>();
        for (String record : raw.split(ITEM_RECORD_SEPARATOR)) {
            String[] fields = record.split(ITEM_FIELD_SEPARATOR, -1);
            if (fields.length < 4) {
                continue;
            }
            UUID itemId = UUID.fromString(fields[0]);
            String originalPhrase = fields[1];
            GenericItemCategory genericCategory = GenericItemCategory.valueOf(fields[2]);
            String activityContext = fields[3].isEmpty() ? null : fields[3];
            items.add(new RequestedItemSummary(itemId, originalPhrase, genericCategory, activityContext));
        }
        return List.copyOf(items);
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<LiveOutfitItem> getItems() {
        return items;
    }
}
