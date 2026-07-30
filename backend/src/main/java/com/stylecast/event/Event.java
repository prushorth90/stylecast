package com.stylecast.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A manually created event a user can style outfits for.
 *
 * This entity is intentionally kept out of the public REST contract; the
 * controller and service layers always translate to/from {@link
 * com.stylecast.event.dto.EventResponse}.
 */
@Entity
@Table(name = "events")
public class Event {

    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column
    private String description;

    @Column(nullable = false, length = 300)
    private String location;

    @Column(name = "start_time", nullable = false)
    private OffsetDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private OffsetDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EventSetting setting;

    @Column(name = "dress_code", length = 100)
    private String dressCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Event() {
        // JPA
    }

    public Event(
            UUID id,
            String title,
            String description,
            String location,
            OffsetDateTime startTime,
            OffsetDateTime endTime,
            EventSetting setting,
            String dressCode,
            Instant createdAt) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.location = location;
        this.startTime = startTime;
        this.endTime = endTime;
        this.setting = setting;
        this.dressCode = dressCode;
        this.createdAt = createdAt;
    }

    /**
     * Applies new field values in place (used by the "Continue" action in
     * the two-step event setup flow when editing a previously saved draft
     * event rather than creating a new one) - the {@code id}/{@code
     * createdAt} never change.
     */
    public void update(
            String title,
            String description,
            String location,
            OffsetDateTime startTime,
            OffsetDateTime endTime,
            EventSetting setting,
            String dressCode) {
        this.title = title;
        this.description = description;
        this.location = location;
        this.startTime = startTime;
        this.endTime = endTime;
        this.setting = setting;
        this.dressCode = dressCode;
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getLocation() {
        return location;
    }

    public OffsetDateTime getStartTime() {
        return startTime;
    }

    public OffsetDateTime getEndTime() {
        return endTime;
    }

    public EventSetting getSetting() {
        return setting;
    }

    public String getDressCode() {
        return dressCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
