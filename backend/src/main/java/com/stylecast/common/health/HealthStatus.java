package com.stylecast.common.health;

/**
 * Simple response body returned by the application health endpoint.
 *
 * @param status  a fixed status literal, e.g. "UP"
 * @param service the name of the service reporting its status
 */
public record HealthStatus(String status, String service) {
}
