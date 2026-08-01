package com.stylecast.auth;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Resolves the currently authenticated user's id from the Spring Security
 * context. Every event-scoped service uses this - never a userId supplied
 * by the request body/path/query - to decide which rows a caller may see
 * or modify (see {@link com.stylecast.event.EventRepository#findByIdAndUserId}).
 */
@Component
public class CurrentUserProvider {

    public UUID requireCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof StyleCastUserPrincipal principal)) {
            throw new AuthenticationCredentialsNotFoundException("No authenticated user in the security context");
        }
        return principal.getUserId();
    }
}
