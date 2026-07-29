package com.stylecast.retail;

/**
 * Behind-the-interface boundary for live retail product search, per
 * ARCHITECTURE.md's rule that every external integration lives behind an
 * interface and controllers/services never call third-party APIs directly.
 *
 * <p>Implementations must:
 * <ul>
 *   <li>Return only {@code https://nordstrom.com} (or an allowed subdomain)
 *       product-page URLs - never other domains, and never search/category/
 *       editorial pages where reasonably detectable.</li>
 *   <li>Never invent a title, price, URL, image, size, or availability that
 *       was not present in (or independently derivable from) the underlying
 *       search result. Missing fields must stay {@code null}/empty.</li>
 *   <li>Return an empty {@link RetailProductSearchResult} (not an exception)
 *       when the search legitimately finds zero matches.</li>
 *   <li>Throw {@link ProductSearchProviderException} - never fall back to
 *       fictional local data - on timeout, provider-side failure, or a
 *       response too malformed to safely interpret.</li>
 * </ul>
 */
public interface RetailProductSearchProvider {

    RetailProductSearchResult search(RetailProductSearchRequest request);
}
