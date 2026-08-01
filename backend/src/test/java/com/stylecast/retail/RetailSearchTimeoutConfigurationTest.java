package com.stylecast.retail;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Confirms {@code application.yml}'s retail-search timeout defaults and
 * environment-variable override names, without booting a Spring context
 * (no Testcontainers/database needed for a config-file assertion like
 * this). {@link OpenAiNordstromProductSearchProviderTest}'s HTTP-layer
 * tests (e.g. {@code search_withLongerConfiguredReadTimeout_...}) already
 * prove that whatever value {@link RetailSearchProperties#readTimeoutMs()}
 * holds is what's actually applied - together these confirm both "the
 * default is 60000ms" and "overriding it still works".
 */
class RetailSearchTimeoutConfigurationTest {

    @Test
    void applicationYml_defaultsRetailSearchReadTimeoutTo60000Ms() throws IOException {
        assertThat(applicationYml()).contains("read-timeout-ms: ${RETAIL_SEARCH_READ_TIMEOUT_MS:60000}");
    }

    @Test
    void applicationYml_keepsRetailSearchConnectTimeoutBoundedTo5000Ms() throws IOException {
        assertThat(applicationYml()).contains("connect-timeout-ms: ${RETAIL_SEARCH_CONNECT_TIMEOUT_MS:5000}");
    }

    private String applicationYml() throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("application.yml")) {
            assertThat(in).as("application.yml must be on the test classpath").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
