package com.stylecast.common.config;

import com.stylecast.common.error.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

/**
 * Returns the same {@link ApiError} JSON shape used everywhere else in the
 * API (instead of Spring Security's default redirect-to-login-page/blank
 * 403 page) when an unauthenticated request hits a protected endpoint. This
 * runs as part of the security filter chain, before the request reaches a
 * controller, so it can't go through {@code GlobalExceptionHandler} - it
 * must serialize the response itself.
 */
@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, org.springframework.security.core.AuthenticationException authException)
            throws java.io.IOException {
        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                "Authentication is required",
                request.getRequestURI(),
                null);

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
