package com.stylecast.weather;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EventWeatherSnapshotRepository extends JpaRepository<EventWeatherSnapshot, UUID> {

    Optional<EventWeatherSnapshot> findByEventId(UUID eventId);
}
