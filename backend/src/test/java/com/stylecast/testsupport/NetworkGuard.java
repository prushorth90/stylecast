package com.stylecast.testsupport;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

/**
 * Fails fast (throws {@link IllegalStateException}) if a configured base
 * URL is anything other than a loopback host - the guard behind {@link
 * NoExternalNetworkGuardConfig}. Used so a test that accidentally loses its
 * {@code application-test.yml}/{@code @ActiveProfiles("test")} override (or
 * a future test that copies an existing class without it) fails
 * immediately and clearly, rather than silently attempting a real call to
 * OpenAI, Nordstrom, or a public weather/geocoding API.
 *
 * <p>Allowed hosts: {@code localhost}, {@code 127.0.0.1}, {@code ::1} (and
 * bracketed {@code [::1]}) - nothing else, ever, regardless of scheme.
 */
public final class NetworkGuard {

    private static final Set<String> ALLOWED_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "[::1]", "0:0:0:0:0:0:0:1");

    private NetworkGuard() {
    }

    /**
     * @throws IllegalStateException if {@code baseUrl} does not resolve to
     *                                an allowed loopback host. A blank
     *                                {@code baseUrl} is allowed (some
     *                                properties, e.g. a blank API key
     *                                paired with any base URL, never result
     *                                in an outbound call at all).
     */
    public static void assertLocalHost(String propertyLabel, String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return;
        }
        String host;
        try {
            host = new URI(baseUrl).getHost();
        } catch (URISyntaxException e) {
            throw new IllegalStateException(
                    "Test configuration property '" + propertyLabel + "' is not a valid URL: " + baseUrl, e);
        }
        if (host == null || !ALLOWED_HOSTS.contains(host.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException(
                    "Refusing to start: test configuration property '" + propertyLabel + "' points at a "
                            + "non-local host (" + host + ", from " + baseUrl + "). Automated tests must never "
                            + "reach a real external host - point it at a local fake server (localhost/127.0.0.1/::1) "
                            + "instead, or substitute a fake provider bean.");
        }
    }
}
