package com.stylecast.auth;

import com.stylecast.user.AppUser;
import com.stylecast.user.AppUserRepository;
import com.stylecast.user.EmailNormalizer;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Loads a {@link StyleCastUserPrincipal} by email for Spring Security's
 * {@code DaoAuthenticationProvider} to authenticate against during login.
 */
@Service
public class StyleCastUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    public StyleCastUserDetailsService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        AppUser user = appUserRepository.findByEmail(EmailNormalizer.normalize(email))
                .orElseThrow(() -> new UsernameNotFoundException("No user with that email"));
        return new StyleCastUserPrincipal(user.getId(), user.getEmail(), user.getPasswordHash());
    }
}
