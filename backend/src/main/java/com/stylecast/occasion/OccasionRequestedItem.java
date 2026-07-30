package com.stylecast.occasion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Persisted form of one explicit {@link RequestedItem} extracted from an
 * event's saved outfit request. Child rows of {@link OccasionInterpretation}
 * - cascade-deleted with their parent, rebuilt (clear + re-add) every time
 * the interpretation is (re)generated, same lifecycle as the parent row
 * itself. Old interpretation rows simply have zero child rows here, which
 * is why {@link OccasionInterpretation#getRequestedItems()} returning an
 * empty list is fully backward compatible - no backfill needed.
 */
@Entity
@Table(name = "occasion_requested_items")
public class OccasionRequestedItem {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interpretation_id", nullable = false)
    private OccasionInterpretation interpretation;

    @Column(name = "original_phrase", nullable = false, length = 200)
    private String originalPhrase;

    @Enumerated(EnumType.STRING)
    @Column(name = "generic_category", nullable = false, length = 20)
    private GenericItemCategory genericCategory;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "search_terms", nullable = false, columnDefinition = "text[]")
    private List<String> searchTerms;

    @Column(nullable = false)
    private boolean required;

    @Column(name = "activity_context", length = 100)
    private String activityContext;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OccasionRequestedItem() {
        // JPA
    }

    public OccasionRequestedItem(RequestedItem item, Instant createdAt) {
        this.id = item.id();
        this.originalPhrase = item.originalPhrase();
        this.genericCategory = item.genericCategory();
        this.searchTerms = List.copyOf(item.searchTerms());
        this.required = item.required();
        this.activityContext = item.activityContext();
        this.displayOrder = item.displayOrder();
        this.createdAt = createdAt;
    }

    void assignTo(OccasionInterpretation interpretation) {
        this.interpretation = interpretation;
    }

    /** Reconstructs the domain {@link RequestedItem} value this row represents. */
    public RequestedItem toRequestedItem() {
        return new RequestedItem(id, originalPhrase, genericCategory, searchTerms, required, activityContext, displayOrder);
    }

    public UUID getId() {
        return id;
    }

    public String getOriginalPhrase() {
        return originalPhrase;
    }

    public GenericItemCategory getGenericCategory() {
        return genericCategory;
    }

    public List<String> getSearchTerms() {
        return searchTerms;
    }

    public boolean isRequired() {
        return required;
    }

    public String getActivityContext() {
        return activityContext;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
