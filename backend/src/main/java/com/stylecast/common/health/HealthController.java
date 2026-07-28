package com.stylecast.common.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin controller exposing a lightweight application health endpoint,
 * separate from Spring Boot Actuator's {@code /actuator/health}.
 */
@RestController
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/api/health")
    public HealthStatus health() {
        return healthService.currentStatus();
    }
}
