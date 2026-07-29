package com.stylecast.retail;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Validates that a URL is a real {@code nordstrom.com} product page
 * (not the bare domain, a search page, a category/browse listing, an
 * editorial article, or an unrelated domain) and canonicalizes URLs for
 * deduplication.
 *
 * <p>Nordstrom product pages follow the {@code /s/<slug>/<numeric-id>} path
 * pattern (e.g. {@code https://www.nordstrom.com/s/mens-suit-jacket/6819331}).
 * This is a heuristic: it rejects everything that is clearly not a product
 * page, but does not itself confirm the page still exists or is in stock.
 */
public final class NordstromUrlValidator {

    private static final String ROOT_DOMAIN = "nordstrom.com";
    private static final String ALLOWED_SUBDOMAIN_SUFFIX = "." + ROOT_DOMAIN;
    private static final Pattern PRODUCT_PATH = Pattern.compile("^/s/[^/]+/\\d+/?$");

    private NordstromUrlValidator() {
    }

    /**
     * @return {@code true} if {@code rawUrl} is an HTTPS nordstrom.com (or
     * allowed subdomain) product-page URL; {@code false} for anything
     * malformed, on a different domain, or not shaped like a product page
     * (search results, category/browse pages, editorial content, the bare
     * homepage, etc.).
     */
    public static boolean isValidNordstromProductUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return false;
        }
        URI uri;
        try {
            uri = new URI(rawUrl);
        } catch (URISyntaxException e) {
            return false;
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        String path = uri.getRawPath();
        if (scheme == null || host == null || path == null) {
            return false;
        }
        if (!"https".equalsIgnoreCase(scheme)) {
            return false;
        }
        String lowerHost = host.toLowerCase(Locale.ROOT);
        boolean allowedHost = lowerHost.equals(ROOT_DOMAIN) || lowerHost.endsWith(ALLOWED_SUBDOMAIN_SUFFIX);
        if (!allowedHost) {
            return false;
        }
        return PRODUCT_PATH.matcher(path).matches();
    }

    /**
     * Canonicalizes a URL already known to pass {@link #isValidNordstromProductUrl}
     * so equivalent URLs (differing only by query string, fragment, trailing
     * slash, or letter case in scheme/host) dedupe to the same key.
     */
    public static String canonicalize(String rawUrl) {
        URI uri = URI.create(rawUrl);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        String path = uri.getRawPath();
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return "https://" + host + path;
    }
}
