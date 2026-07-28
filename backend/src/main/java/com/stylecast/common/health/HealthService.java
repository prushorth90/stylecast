package com.stylecast.common.health;

import org.springframework.stereotype.Service;

/**
 * Provides the current application health status for {@code /api/health}.
 */
@Service
public class HealthService {

    public HealthStatus currentStatus() {
        return new HealthStatus("UP", "stylecast-backend");
    }
}
