package com.stylecast.occasion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OccasionInterpretationRepository extends JpaRepository<OccasionInterpretation, UUID> {

    Optional<OccasionInterpretation> findByEventId(UUID eventId);
}
