package com.stylecast.recommendation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LiveOutfitRecommendationRepository extends JpaRepository<LiveOutfitRecommendation, UUID> {

    List<LiveOutfitRecommendation> findByEventIdAndStatusInOrderByRankPositionAsc(UUID eventId, List<RecommendationStatus> statuses);

    Optional<LiveOutfitRecommendation> findFirstByEventIdOrderByGenerationDesc(UUID eventId);

    List<LiveOutfitRecommendation> findByEventIdAndStatus(UUID eventId, RecommendationStatus status);
}
