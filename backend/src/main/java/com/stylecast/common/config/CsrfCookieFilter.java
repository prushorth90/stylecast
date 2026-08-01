package com.stylecast.common.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Forces the CSRF token to be resolved (and therefore its cookie written)
 * on every request, not only ones that already happen to read it. Without
 * this, {@code CookieCsrfTokenRepository}'s token is loaded lazily and the
 * {@code XSRF-TOKEN} cookie may not exist yet the first time the frontend
 * loads the app (e.g. before any request touches the CSRF token), leaving
 * it with nothing to echo back on the first mutating request. This is
 * Spring Security's own documented pattern for single-page-app CSRF
 * cookies.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            csrfToken.getToken();
        }
        filterChain.doFilter(request, response);
    }
}
