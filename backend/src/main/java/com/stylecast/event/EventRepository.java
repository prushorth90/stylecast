package com.stylecast.event;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

    List<Event> findByEndTimeAfterOrderByStartTimeAsc(OffsetDateTime now);
}
