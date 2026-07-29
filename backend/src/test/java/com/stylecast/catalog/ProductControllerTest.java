package com.stylecast.catalog;

import com.stylecast.catalog.dto.ProductDetailResponse;
import com.stylecast.catalog.dto.ProductPageResponse;
import com.stylecast.catalog.dto.ProductSummaryResponse;
import com.stylecast.common.error.ApiError;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-stack tests against the seeded (Flyway-managed) catalog data. The
 * catalog is baseline seed data rather than per-test fixtures, so tests
 * only ever read/filter it - nothing is created or deleted between tests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class ProductControllerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private ResponseEntity<ProductPageResponse> listProducts(String queryString) {
        String path = "/api/products" + (queryString.isEmpty() ? "" : "?" + queryString);
        return restTemplate.getForEntity(url(path), ProductPageResponse.class);
    }

    @Test

    void seedData_loadsAtLeast60ActiveProducts() {
        ResponseEntity<ProductPageResponse> response = listProducts("pageSize=1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().totalElements()).isGreaterThanOrEqualTo(60);
    }

    @Test
    void listProducts_excludesInactiveProducts() {
        ResponseEntity<ProductPageResponse> response = listProducts("pageSize=100");

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().content())
                .extracting(ProductSummaryResponse::name)
                .doesNotContain("Archived Wool Topcoat");
    }

    @Test
    void listProducts_pagination_isConsistentAndDeterministic() {
        ResponseEntity<ProductPageResponse> firstPage = listProducts("page=0&pageSize=10");
        ResponseEntity<ProductPageResponse> secondPage = listProducts("page=1&pageSize=10");
        ResponseEntity<ProductPageResponse> firstPageAgain = listProducts("page=0&pageSize=10");

        assertThat(firstPage.getBody()).isNotNull();
        assertThat(secondPage.getBody()).isNotNull();
        assertThat(firstPage.getBody().content()).hasSize(10);
        assertThat(secondPage.getBody().content()).hasSize(10);
        assertThat(firstPage.getBody().totalElements()).isEqualTo(secondPage.getBody().totalElements());

        List<UUID> firstIds = firstPage.getBody().content().stream().map(ProductSummaryResponse::id).toList();
        List<UUID> secondIds = secondPage.getBody().content().stream().map(ProductSummaryResponse::id).toList();
        List<UUID> firstIdsAgain = firstPageAgain.getBody().content().stream().map(ProductSummaryResponse::id).toList();

        assertThat(firstIds).isEqualTo(firstIdsAgain);
        assertThat(firstIds).doesNotContainAnyElementsOf(secondIds);
    }

    @Test
    void getProduct_withExistingId_returnsProductWithVariants() {
        ResponseEntity<ProductPageResponse> listResponse = listProducts("pageSize=1");
        UUID productId = listResponse.getBody().content().get(0).id();

        ResponseEntity<ProductDetailResponse> response = restTemplate.getForEntity(
                url("/api/products/" + productId), ProductDetailResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isEqualTo(productId);
        assertThat(response.getBody().variants()).isNotEmpty();
    }

    @Test
    void getProduct_withUnknownId_returns404() {
        UUID unknownId = UUID.randomUUID();

        ResponseEntity<ApiError> response = restTemplate.getForEntity(url("/api/products/" + unknownId), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains(unknownId.toString());
    }

    @Test
    void getProduct_withMalformedId_returns400() {
        ResponseEntity<ApiError> response = restTemplate.getForEntity(url("/api/products/not-a-uuid"), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void listProducts_filterByCategory_returnsOnlyMatchingCategory() {
        ResponseEntity<ProductPageResponse> response = listProducts("category=SUIT&pageSize=100");

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().content()).isNotEmpty();
        assertThat(response.getBody().content())
                .allMatch(product -> product.category() == ProductCategory.SUIT);
    }

    @Test
    void listProducts_filterByClothingSize_returnsOnlyMatchingSize() {
        ResponseEntity<ProductPageResponse> response = listProducts("clothingSize=M&pageSize=100");

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().content()).isNotEmpty();
        assertThat(response.getBody().content())
                .allMatch(product -> product.availableSizes().contains("M"));
    }

    @Test
    void listProducts_filterByColor_isCaseInsensitiveAndReturnsOnlyMatchingColor() {
        ResponseEntity<ProductPageResponse> response = listProducts("color=navy&pageSize=100");

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().content()).isNotEmpty();
        assertThat(response.getBody().content())
                .allMatch(product -> product.availableColors().contains("Navy"));
    }

    @Test
    void listProducts_filterByMaxPrice_returnsOnlyProductsAtOrBelowPrice() {
        ResponseEntity<ProductPageResponse> response = listProducts("maxPrice=50&pageSize=100");

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().content()).isNotEmpty();
        assertThat(response.getBody().content())
                .allMatch(product -> product.startingPrice().doubleValue() <= 50.0);
    }

    @Test
    void listProducts_filterByStyleTag_returnsOnlyMatchingStyle() {
        ResponseEntity<ProductPageResponse> response = listProducts("preferredStyle=BOLD&pageSize=100");

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().content()).isNotEmpty();
        assertThat(response.getBody().content())
                .allMatch(product -> product.styleTags().contains(StyleTag.BOLD));
    }

    @Test
    void listProducts_filterByOccasionTag_returnsOnlyMatchingOccasion() {
        ResponseEntity<ProductPageResponse> response = listProducts("occasion=WEDDING&pageSize=100");

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().content()).isNotEmpty();
        assertThat(response.getBody().content())
                .allMatch(product -> product.occasionTags().contains(OccasionTag.WEDDING));
    }

    @Test
    void listProducts_filterByWeatherTag_returnsOnlyMatchingWeather() {
        ResponseEntity<ProductPageResponse> response = listProducts("weather=RAIN&pageSize=100");

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().content()).isNotEmpty();
        assertThat(response.getBody().content())
                .allMatch(product -> product.weatherTags().contains(WeatherTag.RAIN));
    }

    @Test
    void listProducts_filterByFormalityRange_returnsOnlyProductsWithinRange() {
        ResponseEntity<ProductPageResponse> response =
                listProducts("minimumFormality=8&maximumFormality=10&pageSize=100");

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().content()).isNotEmpty();
        assertThat(response.getBody().content())
                .allMatch(product -> product.formalityLevel() >= 8 && product.formalityLevel() <= 10);
    }

    @Test
    void listProducts_filterByInStock_excludesFullyOutOfStockProduct() {
        ResponseEntity<ProductPageResponse> response = listProducts("inStock=true&pageSize=100");

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().content()).allMatch(ProductSummaryResponse::inStock);
        assertThat(response.getBody().content())
                .extracting(ProductSummaryResponse::name)
                .doesNotContain("Sold Out Statement Coat");
    }

    @Test
    void listProducts_withInStockFilterAlone_doesNotReturnDuplicateProducts() {
        ResponseEntity<ProductPageResponse> response = listProducts("inStock=true&pageSize=100");

        assertThat(response.getBody()).isNotNull();
        List<UUID> ids = response.getBody().content().stream().map(ProductSummaryResponse::id).toList();

        assertThat(ids).doesNotHaveDuplicates();
        assertThat(ids).hasSize((int) ids.stream().distinct().count());
    }

    @Test
    void listProducts_combinedFilters_appliesEveryFilter() {
        ResponseEntity<ProductPageResponse> response = listProducts(
                "category=OUTERWEAR&minimumFormality=6&maximumFormality=7&weather=COLD&pageSize=100");

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().content()).isNotEmpty();
        assertThat(response.getBody().content()).allMatch(product ->
                product.category() == ProductCategory.OUTERWEAR
                        && product.formalityLevel() >= 6 && product.formalityLevel() <= 7
                        && product.weatherTags().contains(WeatherTag.COLD));
    }

    @Test
    void listProducts_invalidCategory_returns400() {
        ResponseEntity<ApiError> response = restTemplate.getForEntity(
                url("/api/products?category=NOT_A_CATEGORY"), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void listProducts_invalidStyleTag_returns400() {
        ResponseEntity<ApiError> response = restTemplate.getForEntity(
                url("/api/products?preferredStyle=NOT_A_STYLE"), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void listProducts_negativeMaxPrice_returns400() {
        ResponseEntity<ApiError> response = restTemplate.getForEntity(
                url("/api/products?maxPrice=-10"), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("maxPrice");
    }

    @Test
    void listProducts_formalityOutOfRange_returns400() {
        ResponseEntity<ApiError> lowResponse = restTemplate.getForEntity(
                url("/api/products?minimumFormality=0"), ApiError.class);
        ResponseEntity<ApiError> highResponse = restTemplate.getForEntity(
                url("/api/products?maximumFormality=11"), ApiError.class);

        assertThat(lowResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(highResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void listProducts_minimumFormalityGreaterThanMaximum_returns400() {
        ResponseEntity<ApiError> response = restTemplate.getForEntity(
                url("/api/products?minimumFormality=9&maximumFormality=3"), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
