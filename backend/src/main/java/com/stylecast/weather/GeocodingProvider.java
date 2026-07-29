package com.stylecast.weather;

/**
 * Converts a free-text event location into geographic coordinates.
 * Implementations must never fabricate a location: if the text cannot be
 * resolved, throw {@link UnresolvableLocationException}.
 */
public interface GeocodingProvider {

    /**
     * @throws UnresolvableLocationException if the location cannot be resolved
     * @throws GeocodingProviderException     on a transient/provider failure (timeout, non-2xx, malformed response)
     */
    GeocodedLocation geocode(String location);
}
