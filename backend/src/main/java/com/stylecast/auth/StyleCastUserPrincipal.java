package com.stylecast.auth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Spring Security principal wrapping a StyleCast {@link com.stylecast.user.AppUser}.
 * Only a normal-user role exists in this MVP (no roles/admin distinction).
 */
public class StyleCastUserPrincipal implements UserDetails {

    private final UUID userId;
    private final String email;
    private final String passwordHash;

    public StyleCastUserPrincipal(UUID userId, String email, String passwordHash) {
        this.userId = userId;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public UUID getUserId() {
        return userId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }
}
