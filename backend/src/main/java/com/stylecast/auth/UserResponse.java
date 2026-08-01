package com.stylecast.auth;

import com.stylecast.user.AppUser;

import java.util.UUID;

/**
 * The only shape a user is ever returned in - {@code passwordHash} never
 * appears here.
 */
public record UserResponse(UUID id, String email) {

    public static UserResponse fromEntity(AppUser user) {
        return new UserResponse(user.getId(), user.getEmail());
    }
}
