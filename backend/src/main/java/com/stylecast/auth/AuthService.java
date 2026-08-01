package com.stylecast.auth;

import com.stylecast.user.AppUser;
import com.stylecast.user.AppUserRepository;
import com.stylecast.user.EmailNormalizer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Registration business logic (email normalization, uniqueness, password
 * hashing). Login is handled by {@link AuthController} directly via Spring
 * Security's {@code AuthenticationManager}, since it needs the current
 * {@code HttpServletRequest}/{@code HttpServletResponse} to persist the
 * resulting security context into the session.
 */
@Service
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserResponse register(RegisterRequest request) {
        String normalizedEmail = EmailNormalizer.normalize(request.email());

        if (appUserRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyRegisteredException();
        }

        Instant now = Instant.now();
        AppUser user = new AppUser(
                UUID.randomUUID(),
                normalizedEmail,
                passwordEncoder.encode(request.password()),
                now,
                now);

        AppUser saved = appUserRepository.save(user);
        return UserResponse.fromEntity(saved);
    }
}
