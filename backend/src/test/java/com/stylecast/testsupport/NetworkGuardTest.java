package com.stylecast.testsupport;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pure unit tests for {@link NetworkGuard} - the logic behind {@link NoExternalNetworkGuardConfig}. */
class NetworkGuardTest {

    @Test
    void assertLocalHost_withLocalhostUrl_doesNotThrow() {
        assertThatCode(() -> NetworkGuard.assertLocalHost("label", "http://localhost:1234"))
                .doesNotThrowAnyException();
    }

    @Test
    void assertLocalHost_with127_0_0_1Url_doesNotThrow() {
        assertThatCode(() -> NetworkGuard.assertLocalHost("label", "http://127.0.0.1:8080"))
                .doesNotThrowAnyException();
    }

    @Test
    void assertLocalHost_withIpv6LoopbackUrl_doesNotThrow() {
        assertThatCode(() -> NetworkGuard.assertLocalHost("label", "http://[::1]:8080"))
                .doesNotThrowAnyException();
    }

    @Test
    void assertLocalHost_withBlankUrl_doesNotThrow() {
        assertThatCode(() -> NetworkGuard.assertLocalHost("label", "")).doesNotThrowAnyException();
        assertThatCode(() -> NetworkGuard.assertLocalHost("label", null)).doesNotThrowAnyException();
    }

    @Test
    void assertLocalHost_withRealOpenAiHost_throws() {
        assertThatThrownBy(() -> NetworkGuard.assertLocalHost("stylecast.retail-search.base-url", "https://api.openai.com/v1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("api.openai.com");
    }

    @Test
    void assertLocalHost_withRealNordstromMediaHost_throws() {
        assertThatThrownBy(() -> NetworkGuard.assertLocalHost("label", "https://n.nordstrommedia.com/it/abc.jpeg"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("n.nordstrommedia.com");
    }

    @Test
    void assertLocalHost_withRealWeatherHost_throws() {
        assertThatThrownBy(() -> NetworkGuard.assertLocalHost("label", "https://api.open-meteo.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("api.open-meteo.com");
    }

    @Test
    void assertLocalHost_withMalformedUrl_throws() {
        assertThatThrownBy(() -> NetworkGuard.assertLocalHost("label", "not a valid url ::"))
                .isInstanceOf(IllegalStateException.class);
    }
}
