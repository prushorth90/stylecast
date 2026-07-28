package com.stylecast.event.styling;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EventStylePreferencesRepository extends JpaRepository<EventStylePreferences, UUID> {

    Optional<EventStylePreferences> findByEventId(UUID eventId);
}
