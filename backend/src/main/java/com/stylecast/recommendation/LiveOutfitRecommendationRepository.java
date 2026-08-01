package com.stylecast.recommendation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LiveOutfitRecommendationRepository extends JpaRepository<LiveOutfitRecommendation, UUID> {

    List<LiveOutfitRecommendation> findByEventIdAndStatusInOrderByRankPositionAsc(UUID eventId, List<RecommendationStatus> statuses);

    Optional<LiveOutfitRecommendation> findFirstByEventIdOrderByGenerationDesc(UUID eventId);

    List<LiveOutfitRecommendation> findByEventIdAndStatus(UUID eventId, RecommendationStatus status);

    /**
     * Batch "latest generation per event" lookup for the calendar endpoint -
     * one query instead of one per visible event. Rows for the same event are
     * grouped together and ordered by {@code generation} descending, so the
     * FIRST row encountered for each event id (when iterating in order) is
     * always that event's latest generation - completeness/stale/generation
     * are denormalized identically across every row of one generation, so any
     * single row for the max generation is sufficient.
     */
    List<LiveOutfitRecommendation> findByEventIdInOrderByEventIdAscGenerationDesc(Collection<UUID> eventIds);
}
