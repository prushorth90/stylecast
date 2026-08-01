package com.stylecast.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A registered StyleCast user. Owns events (see {@link com.stylecast.event.Event}).
 *
 * Kept out of the public REST contract - {@link com.stylecast.auth.UserResponse}
 * is what the API ever returns, so {@code passwordHash} can never leak in a
 * response body.
 */
@Entity
@Table(name = "app_users")
public class AppUser {

    @Id
    private UUID id;

    /** Always normalized (trimmed + lowercased) - see {@link EmailNormalizer}. */
    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AppUser() {
        // JPA
    }

    public AppUser(UUID id, String email, String passwordHash, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
