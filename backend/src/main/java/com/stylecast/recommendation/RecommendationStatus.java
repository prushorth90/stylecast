package com.stylecast.recommendation;

/**
 * Lifecycle status of a persisted {@link OutfitRecommendation} row.
 *
 * <p>Regeneration is versioned: each call to {@code POST
 * /api/events/{eventId}/recommendations/generate} creates a new {@code
 * generation} number. Rows from the previous generation are kept (marked
 * {@link #SUPERSEDED}) rather than deleted, so history is preserved, while
 * {@code GET} and the "current recommendations" view only ever consider the
 * latest generation's rows.
 */
public enum RecommendationStatus {

    /** Belongs to the latest generation and represents a real generated outfit. */
    ACTIVE,

    /** Belonged to an earlier generation, replaced by a later regeneration. */
    SUPERSEDED,

    /**
     * The latest generation ran but no combination of catalog products
     * satisfied every hard constraint. A single row with this status is
     * persisted per such generation (no items), so a repeated {@code GET}
     * can distinguish "generated, but nothing valid was found" from "never
     * generated" without re-running generation.
     */
    NO_VALID_OUTFIT
}
