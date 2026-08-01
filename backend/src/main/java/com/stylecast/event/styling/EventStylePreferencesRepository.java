package com.stylecast.event.styling;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventStylePreferencesRepository extends JpaRepository<EventStylePreferences, UUID> {

    Optional<EventStylePreferences> findByEventId(UUID eventId);

    /** Batch existence lookup for the calendar endpoint - avoids one query per visible event. */
    List<EventStylePreferences> findByEventIdIn(Collection<UUID> eventIds);
}
