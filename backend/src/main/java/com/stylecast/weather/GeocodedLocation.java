package com.stylecast.weather;

/**
 * The result of successfully geocoding a free-text event location: the
 * provider's human-readable resolved name (for display/transparency) plus
 * the coordinates used to request a forecast.
 */
public record GeocodedLocation(String resolvedName, GeoCoordinates coordinates) {
}
