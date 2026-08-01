package com.stylecast.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registration, login, and "who am I" endpoints. Logout is handled entirely
 * by Spring Security's built-in logout filter (see {@code
 * com.stylecast.common.config.SecurityConfig}), not a controller method.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    public AuthController(
            AuthService authService,
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository) {
        this.authService = authService;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public UserResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        Authentication authenticationRequest =
                new UsernamePasswordAuthenticationToken(request.email(), request.password());

        Authentication authenticationResult;
        try {
            authenticationResult = authenticationManager.authenticate(authenticationRequest);
        } catch (AuthenticationException ex) {
            throw new InvalidCredentialsException();
        }

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authenticationResult);
        SecurityContextHolder.setContext(securityContext);
        securityContextRepository.saveContext(securityContext, httpRequest, httpResponse);

        StyleCastUserPrincipal principal = (StyleCastUserPrincipal) authenticationResult.getPrincipal();
        return new UserResponse(principal.getUserId(), principal.getUsername());
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal StyleCastUserPrincipal principal) {
        return new UserResponse(principal.getUserId(), principal.getUsername());
    }
}
