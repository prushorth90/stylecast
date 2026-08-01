package com.stylecast.occasion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OccasionInterpretationRepository extends JpaRepository<OccasionInterpretation, UUID> {

    Optional<OccasionInterpretation> findByEventId(UUID eventId);

    /** Batch existence lookup for the calendar endpoint - avoids one query per visible event. */
    List<OccasionInterpretation> findByEventIdIn(Collection<UUID> eventIds);
}
