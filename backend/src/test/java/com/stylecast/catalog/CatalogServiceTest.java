package com.stylecast.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CatalogService}'s filter validation and pagination
 * defaulting, isolated from the database via a mocked {@link
 * ProductRepository}.
 */
@ExtendWith(MockitoExtension.class)
class CatalogServiceTest {

    @Mock
    private ProductRepository productRepository;

    private CatalogService catalogService() {
        return new CatalogService(productRepository);
    }

    private ProductSearchCriteria emptyCriteria() {
        return new ProductSearchCriteria(null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    void listProducts_withNegativeMaxPrice_throwsInvalidProductFilterException() {
        ProductSearchCriteria criteria = new ProductSearchCriteria(
                null, null, null, BigDecimal.valueOf(-1), null, null, null, null, null, null);

        assertThatThrownBy(() -> catalogService().listProducts(criteria, 0, 20))
                .isInstanceOf(InvalidProductFilterException.class)
                .hasMessageContaining("maxPrice");
    }

    @Test
    void listProducts_withMinimumFormalityBelowRange_throwsInvalidProductFilterException() {
        ProductSearchCriteria criteria = new ProductSearchCriteria(
                null, null, null, null, null, null, null, 0, null, null);

        assertThatThrownBy(() -> catalogService().listProducts(criteria, 0, 20))
                .isInstanceOf(InvalidProductFilterException.class);
    }

    @Test
    void listProducts_withMaximumFormalityAboveRange_throwsInvalidProductFilterException() {
        ProductSearchCriteria criteria = new ProductSearchCriteria(
                null, null, null, null, null, null, null, null, 11, null);

        assertThatThrownBy(() -> catalogService().listProducts(criteria, 0, 20))
                .isInstanceOf(InvalidProductFilterException.class);
    }

    @Test
    void listProducts_withMinimumFormalityAboveMaximum_throwsInvalidProductFilterException() {
        ProductSearchCriteria criteria = new ProductSearchCriteria(
                null, null, null, null, null, null, null, 8, 3, null);

        assertThatThrownBy(() -> catalogService().listProducts(criteria, 0, 20))
                .isInstanceOf(InvalidProductFilterException.class)
                .hasMessageContaining("minimumFormality");
    }

    @Test
    void listProducts_withNegativePage_throwsInvalidProductFilterException() {
        assertThatThrownBy(() -> catalogService().listProducts(emptyCriteria(), -1, 20))
                .isInstanceOf(InvalidProductFilterException.class);
    }

    @Test
    void listProducts_withPageSizeAboveMax_throwsInvalidProductFilterException() {
        assertThatThrownBy(() -> catalogService().listProducts(emptyCriteria(), 0, 101))
                .isInstanceOf(InvalidProductFilterException.class);
    }

    @Test
    void listProducts_withZeroOrNegativePageSize_fallsBackToDefault() {
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Pageable pageable = invocation.getArgument(1);
                    assertThat(pageable.getPageSize()).isEqualTo(CatalogService.DEFAULT_PAGE_SIZE);
                    return new PageImpl<Product>(List.of(), pageable, 0);
                });

        catalogService().listProducts(emptyCriteria(), 0, 0);
    }

    @Test
    void listProducts_withValidCriteria_returnsMappedPage() {
        Page<Product> emptyPage = new PageImpl<>(List.of());
        when(productRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage);

        var result = catalogService().listProducts(emptyCriteria(), 0, 20);

        assertThat(result.content()).isEmpty();
        assertThat(result.page()).isZero();
        assertThat(result.pageSize()).isEqualTo(20);
    }
}
