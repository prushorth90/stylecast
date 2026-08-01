package com.stylecast.event;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

    /**
     * The single ownership-enforcing lookup every service uses instead of
     * plain {@code findById} - an event that exists but belongs to a
     * different user returns empty, identical to a truly unknown id.
     */
    Optional<Event> findByIdAndUserId(UUID id, UUID userId);

    List<Event> findByUserIdAndEndTimeAfterOrderByStartTimeAsc(UUID userId, OffsetDateTime now);

    List<Event> findByUserIdOrderByStartTimeDesc(UUID userId);

    /**
     * Every one of the user's events that overlaps the half-open interval
     * {@code [rangeStart, rangeEnd)} - backs the calendar endpoint. Standard
     * interval-overlap condition ({@code startTime < rangeEnd AND endTime >
     * rangeStart}), so this correctly includes events that start before the
     * range but end inside it, and events that span multiple days entirely
     * covering the range.
     */
    List<Event> findByUserIdAndStartTimeLessThanAndEndTimeGreaterThanOrderByStartTimeAsc(
            UUID userId, OffsetDateTime rangeEnd, OffsetDateTime rangeStart);
}
