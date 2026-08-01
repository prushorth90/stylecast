package com.stylecast.testsupport;

import com.stylecast.occasion.OccasionClassifierProperties;
import com.stylecast.retail.RetailSearchProperties;
import com.stylecast.weather.WeatherProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;

/**
 * Import into any {@code @SpringBootTest} to fail the test immediately
 * (during application startup, before any test method runs) if the
 * resolved {@link RetailSearchProperties}/{@link OccasionClassifierProperties}/
 * {@link WeatherProperties} base URLs are anything other than a loopback
 * host - see {@link NetworkGuard}.
 *
 * <p>This is deliberately a configuration-level check, not a live network
 * interceptor: it validates the exact URLs the real provider beans would
 * use if invoked, which is enough to catch the realistic failure mode (a
 * test class missing {@code @ActiveProfiles("test")}, or a future test
 * copied from an existing one without the profile) without the much
 * larger blast radius of a JVM-wide DNS/socket guard (which risks
 * collateral breakage of Testcontainers, JVM diagnostics, or other
 * legitimate local networking).
 */
@TestConfiguration
public class NoExternalNetworkGuardConfig {

    @Bean
    @Order(Integer.MIN_VALUE)
    ApplicationRunner externalNetworkGuardRunner(
            RetailSearchProperties retailSearchProperties,
            OccasionClassifierProperties occasionClassifierProperties,
            WeatherProperties weatherProperties) {
        return (ApplicationArguments args) -> {
            NetworkGuard.assertLocalHost("stylecast.retail-search.base-url", retailSearchProperties.baseUrl());
            NetworkGuard.assertLocalHost("stylecast.occasion-classifier.base-url", occasionClassifierProperties.baseUrl());
            NetworkGuard.assertLocalHost("stylecast.weather.geocoding-base-url", weatherProperties.geocodingBaseUrl());
            NetworkGuard.assertLocalHost("stylecast.weather.forecast-base-url", weatherProperties.forecastBaseUrl());
        };
    }
}
