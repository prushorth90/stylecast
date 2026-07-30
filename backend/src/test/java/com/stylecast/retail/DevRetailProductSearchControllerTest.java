package com.stylecast.retail;

import com.stylecast.catalog.ProductCategory;
import com.stylecast.common.error.ApiError;
import com.stylecast.retail.dto.RetailProductSearchApiRequest;
import com.stylecast.retail.dto.RetailProductSearchApiResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-request tests for {@code POST /api/dev/retail-products/search}. The
 * real {@link RetailProductSearchProvider} is replaced with a fake bean
 * ({@link FakeProviderConfig}) so these tests never call the live OpenAI
 * API - only request validation and HTTP-level error mapping are exercised
 * here (provider response normalization is covered by
 * {@code OpenAiNordstromProductSearchProviderTest}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class DevRetailProductSearchControllerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private FakeRetailProductSearchProvider fakeProvider;

    @AfterEach
    void resetFake() {
        fakeProvider.reset();
    }

    private String url() {
        return "http://localhost:" + port + "/api/dev/retail-products/search";
    }

    @Test
    void search_withoutCategoryOrKeywords_returns400() {
        RetailProductSearchApiRequest request = new RetailProductSearchApiRequest(
                Retailer.NORDSTROM, null, List.of(), null, null, null);

        ResponseEntity<ApiError> response = restTemplate.postForEntity(url(), request, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void search_withUnsupportedRetailer_returns400() {
        String rawJsonWithBadRetailer = "{\"retailer\":\"TARGET\",\"category\":\"SUIT\"}";
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        var entity = new org.springframework.http.HttpEntity<>(rawJsonWithBadRetailer, headers);

        ResponseEntity<ApiError> response = restTemplate.postForEntity(url(), entity, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void search_withNonPositiveMaxPrice_returns400() {
        RetailProductSearchApiRequest request = new RetailProductSearchApiRequest(
                Retailer.NORDSTROM, ProductCategory.SUIT, List.of(), BigDecimal.ZERO, null, null);

        ResponseEntity<ApiError> response = restTemplate.postForEntity(url(), request, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void search_withLimitAboveMax_returns400() {
        RetailProductSearchApiRequest request = new RetailProductSearchApiRequest(
                Retailer.NORDSTROM, ProductCategory.SUIT, List.of(), null, null, 999);

        ResponseEntity<ApiError> response = restTemplate.postForEntity(url(), request, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void search_withZeroValidCandidates_returns200WithEmptyList() {
        fakeProvider.nextResult.set(new RetailProductSearchResult(List.of()));
        RetailProductSearchApiRequest request = new RetailProductSearchApiRequest(
                Retailer.NORDSTROM, ProductCategory.SUIT, List.of("impossible constraint"), null, null, null);

        ResponseEntity<RetailProductSearchApiResponse> response =
                restTemplate.postForEntity(url(), request, RetailProductSearchApiResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().candidates()).isEmpty();
    }

    @Test
    void search_withValidCandidates_returns200WithCandidates() {
        RetailProductCandidate candidate = new RetailProductCandidate(
                RetailProductSource.AI_WEB_SEARCH, Retailer.NORDSTROM, "Navy Wedding Suit", null,
                null, null, null, null, "https://www.nordstrom.com/s/navy-wedding-suit/1234567",
                null, null, null, List.of(), null, false, false, false, CandidateAudience.UNKNOWN,
                Instant.now(), "fake");
        fakeProvider.nextResult.set(new RetailProductSearchResult(List.of(candidate)));
        RetailProductSearchApiRequest request = new RetailProductSearchApiRequest(
                Retailer.NORDSTROM, ProductCategory.SUIT, List.of("navy", "wedding"),
                new BigDecimal("600"), null, null);

        ResponseEntity<RetailProductSearchApiResponse> response =
                restTemplate.postForEntity(url(), request, RetailProductSearchApiResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().candidates()).hasSize(1);
        assertThat(response.getBody().candidates().get(0).productUrl())
                .isEqualTo("https://www.nordstrom.com/s/navy-wedding-suit/1234567");
    }

    @Test
    void search_whenProviderFails_returns503() {
        fakeProvider.nextFailure.set(new ProductSearchProviderException("provider unavailable"));
        RetailProductSearchApiRequest request = new RetailProductSearchApiRequest(
                Retailer.NORDSTROM, ProductCategory.SUIT, List.of(), null, null, null);

        ResponseEntity<ApiError> response = restTemplate.postForEntity(url(), request, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("provider unavailable");
    }

    @TestConfiguration
    static class FakeProviderConfig {
        @Bean
        @Primary
        FakeRetailProductSearchProvider fakeRetailProductSearchProvider() {
            return new FakeRetailProductSearchProvider();
        }
    }

    static class FakeRetailProductSearchProvider implements RetailProductSearchProvider {
        final AtomicReference<RetailProductSearchResult> nextResult =
                new AtomicReference<>(new RetailProductSearchResult(List.of()));
        final AtomicReference<RuntimeException> nextFailure = new AtomicReference<>();

        @Override
        public RetailProductSearchResult search(RetailProductSearchRequest request) {
            RuntimeException failure = nextFailure.get();
            if (failure != null) {
                throw failure;
            }
            return nextResult.get();
        }

        void reset() {
            nextResult.set(new RetailProductSearchResult(List.of()));
            nextFailure.set(null);
        }
    }
}
