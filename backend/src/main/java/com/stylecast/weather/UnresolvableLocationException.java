package com.stylecast.weather;

/**
 * Thrown when a free-text event location cannot be resolved to coordinates
 * (e.g. it does not match any known place). Mapped to HTTP 422 by the
 * global exception handler - this is a client-data problem (the event's
 * location), not a transient provider failure.
 */
public class UnresolvableLocationException extends RuntimeException {

    public UnresolvableLocationException(String location) {
        super("Could not resolve location to coordinates: " + location);
    }
}
