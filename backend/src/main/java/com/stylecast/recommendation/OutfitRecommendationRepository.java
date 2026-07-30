package com.stylecast.recommendation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutfitRecommendationRepository extends JpaRepository<OutfitRecommendation, UUID> {

    List<OutfitRecommendation> findByEventIdAndStatusInOrderByRankPositionAsc(UUID eventId, List<RecommendationStatus> statuses);

    Optional<OutfitRecommendation> findFirstByEventIdOrderByGenerationDesc(UUID eventId);

    List<OutfitRecommendation> findByEventIdAndStatus(UUID eventId, RecommendationStatus status);
}
