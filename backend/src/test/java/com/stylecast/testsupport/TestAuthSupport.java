package com.stylecast.testsupport;

import com.stylecast.auth.UserResponse;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.ResponseErrorHandler;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Registers and logs in a fresh, uniquely-emailed test user against a
 * running {@code @SpringBootTest} instance, entirely through the real
 * {@code /api/auth/**} endpoints (no shortcuts/backdoors) - so every
 * existing controller test, now that every endpoint requires
 * authentication, can become an authenticated request with a small,
 * mechanical change instead of rewriting each individual HTTP call.
 *
 * <p>{@link #installAuthenticatedUser} adds a {@link ClientHttpRequestInterceptor}
 * directly onto the {@link TestRestTemplate}'s underlying {@code RestTemplate}
 * so every subsequent call made through it - regardless of which
 * convenience method (getForEntity/postForEntity/exchange/...) - is sent as
 * that user, with zero changes to the call sites themselves. Since the
 * underlying {@code RestTemplate} is a shared Spring-managed bean (reused
 * across test methods, and potentially across test classes when Spring
 * caches an identical context), callers MUST remove the interceptor again
 * in {@code @AfterEach} via {@link #uninstall} to avoid leaking one test's
 * session into another.
 *
 * <p>{@link #registerAndLogin} deliberately performs the register/login
 * handshake itself through a brand-new, throwaway {@link RestTemplate}
 * rather than the caller's (possibly already-authenticated-as-someone-else)
 * {@link TestRestTemplate} - this is what makes it safe to call a second
 * time from within a test whose class-level {@code @BeforeEach} already
 * installed an interceptor for a different "current" user (the standard
 * cross-user ownership test pattern): the two handshakes never interfere
 * with each other's cookies.
 */
public final class TestAuthSupport {

    private static final String PASSWORD = "TestPassword123!";


    private TestAuthSupport() {
    }

    /** A logged-in test user: their id (for constructing owned fixtures directly via a repository) and the headers that authenticate as them. */
    public record AuthenticatedTestUser(UUID userId, HttpHeaders headers) {
    }

    /** An authenticated user whose headers have been installed as a shared interceptor - keep this around to {@link #uninstall} it later. */
    public record InstalledAuth(UUID userId, ClientHttpRequestInterceptor interceptor) {
    }

    public static InstalledAuth installAuthenticatedUser(TestRestTemplate restTemplate, int port) {
        AuthenticatedTestUser user = registerAndLogin(restTemplate, port);
        ClientHttpRequestInterceptor interceptor = (request, body, execution) -> {
            request.getHeaders().addAll(user.headers());
            return execution.execute(request, body);
        };
        restTemplate.getRestTemplate().getInterceptors().add(interceptor);
        return new InstalledAuth(user.userId(), interceptor);
    }

    public static void uninstall(TestRestTemplate restTemplate, InstalledAuth installed) {
        restTemplate.getRestTemplate().getInterceptors().remove(installed.interceptor());
    }

    /**
     * Registers a fresh, uniquely-emailed user and logs in, returning their
     * id plus the {@link HttpHeaders} (session cookie + CSRF cookie/header
     * pair) needed to authenticate as them on subsequent requests.
     *
     * <p>The {@code restTemplate} parameter is used only to know which port
     * to call - the actual handshake runs on a brand-new, throwaway {@link
     * RestTemplate} so this is safe to call even when the passed-in {@link
     * TestRestTemplate} already has a different user's auth interceptor
     * installed (the interceptor would otherwise inject that other user's
     * cookies into this handshake's requests too, corrupting both).
     */
    public static AuthenticatedTestUser registerAndLogin(TestRestTemplate restTemplate, int port) {
        RestTemplate httpClient = new RestTemplate();
        // The primer request below is EXPECTED to come back 401 (unauthenticated) -
        // the default error handler would throw for that; this mirrors
        // TestRestTemplate's own lenient (never-throw) default behavior.
        httpClient.setErrorHandler(new ResponseErrorHandler() {
            @Override
            public boolean hasError(org.springframework.http.client.ClientHttpResponse response) {
                return false;
            }
        });
        String baseUrl = "http://localhost:" + port;
        String email = "user-" + UUID.randomUUID() + "@example.com";
        Map<String, String> credentials = Map.of("email", email, "password", PASSWORD);

        // CSRF requires a token cookie to already exist before the first
        // unsafe (POST) request - obtained here via any safe GET, exactly
        // like a real browser loading the app before submitting a login
        // form. The response status/body are irrelevant here; only the
        // Set-Cookie header matters, so the response is read as a plain
        // String to avoid coupling to any particular response shape.
        ResponseEntity<String> primer = httpClient.getForEntity(baseUrl + "/api/auth/me", String.class);
        String csrfToken = extractCookieValue(primer.getHeaders(), "XSRF-TOKEN");

        HttpHeaders csrfHeaders = new HttpHeaders();
        csrfHeaders.setContentType(MediaType.APPLICATION_JSON);
        if (csrfToken != null) {
            csrfHeaders.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrfToken);
            csrfHeaders.add("X-XSRF-TOKEN", csrfToken);
        }

        httpClient.exchange(
                baseUrl + "/api/auth/register",
                HttpMethod.POST,
                new HttpEntity<>(credentials, csrfHeaders),
                UserResponse.class);

        ResponseEntity<UserResponse> loginResponse = httpClient.exchange(
                baseUrl + "/api/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(credentials, csrfHeaders),
                UserResponse.class);

        // The login response's own Set-Cookie headers only ever carry the
        // NEW session cookie (JSESSIONID) reliably - Spring Security's CSRF
        // machinery deliberately skips re-issuing an unchanged XSRF-TOKEN
        // cookie on a request that already presented the same value, so it
        // must be added back in explicitly here rather than assumed to be
        // present in the login response.
        String sessionCookiePairs = combineCookiePairs(loginResponse.getHeaders());
        String finalCsrfToken = extractCookieValue(loginResponse.getHeaders(), "XSRF-TOKEN");
        if (finalCsrfToken == null) {
            finalCsrfToken = csrfToken;
        }

        HttpHeaders authHeaders = new HttpHeaders();
        StringBuilder cookieHeaderValue = new StringBuilder(sessionCookiePairs);
        if (finalCsrfToken != null) {
            if (!cookieHeaderValue.isEmpty()) {
                cookieHeaderValue.append("; ");
            }
            cookieHeaderValue.append("XSRF-TOKEN=").append(finalCsrfToken);
            authHeaders.add("X-XSRF-TOKEN", finalCsrfToken);
        }
        authHeaders.add(HttpHeaders.COOKIE, cookieHeaderValue.toString());

        UserResponse loggedInUser = loginResponse.getBody();
        return new AuthenticatedTestUser(loggedInUser.id(), authHeaders);
    }


    private static String extractCookieValue(HttpHeaders headers, String cookieName) {
        List<String> setCookieHeaders = headers.get(HttpHeaders.SET_COOKIE);
        if (setCookieHeaders == null) {
            return null;
        }
        for (String header : setCookieHeaders) {
            String[] nameAndValue = header.split(";", 2)[0].split("=", 2);
            if (nameAndValue.length == 2 && nameAndValue[0].equals(cookieName)) {
                return nameAndValue[1];
            }
        }
        return null;
    }

    private static String combineCookiePairs(HttpHeaders headers) {
        List<String> setCookieHeaders = headers.get(HttpHeaders.SET_COOKIE);
        if (setCookieHeaders == null) {
            return "";
        }
        return setCookieHeaders.stream()
                .map(header -> header.split(";", 2)[0])
                .collect(Collectors.joining("; "));
    }
}

