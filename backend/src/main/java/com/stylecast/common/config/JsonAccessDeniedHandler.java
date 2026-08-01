package com.stylecast.common.config;

import com.stylecast.common.error.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

/**
 * Returns the same {@link ApiError} JSON shape as the rest of the API for
 * an authenticated-but-forbidden request. In practice this mostly fires for
 * a missing/invalid CSRF token (Spring Security reports that as an {@link
 * AccessDeniedException}), since this app has no roles beyond a normal
 * authenticated user.
 */
@Component
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JsonAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws java.io.IOException {
        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                "Access is denied",
                request.getRequestURI(),
                null);

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/json");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
