package com.stylecast.retail;

import com.stylecast.catalog.ProductCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for request validation and delegation. Uses a mocked
 * {@link RetailProductSearchProvider} - never calls a real provider - per
 * Task 4B's requirement that automated tests must not call the live
 * OpenAI API.
 */
@ExtendWith(MockitoExtension.class)
class RetailProductSearchServiceTest {

    @Mock
    private RetailProductSearchProvider provider;

    private final RetailSearchProperties properties =
            new RetailSearchProperties("test-key", "gpt-4.1", "http://localhost", 1000, 1000, 25, 4);

    private RetailProductSearchService service() {
        return new RetailProductSearchService(provider, properties);
    }

    @Test
    void search_withoutCategoryOrKeywords_throwsInvalidRequest_andNeverCallsProvider() {
        RetailProductSearchRequest request = new RetailProductSearchRequest(
                Retailer.NORDSTROM, null, List.of(), null, null, 0);

        assertThatThrownBy(() -> service().search(request))
                .isInstanceOf(InvalidRetailSearchRequestException.class);
        verifyNoInteractions(provider);
    }

    @Test
    void search_withZeroMaxPrice_throwsInvalidRequest() {
        RetailProductSearchRequest request = new RetailProductSearchRequest(
                Retailer.NORDSTROM, ProductCategory.SUIT, List.of(), BigDecimal.ZERO, null, 0);

        assertThatThrownBy(() -> service().search(request))
                .isInstanceOf(InvalidRetailSearchRequestException.class);
        verifyNoInteractions(provider);
    }

    @Test
    void search_withNegativeMaxPrice_throwsInvalidRequest() {
        RetailProductSearchRequest request = new RetailProductSearchRequest(
                Retailer.NORDSTROM, ProductCategory.SUIT, List.of(), new BigDecimal("-1"), null, 0);

        assertThatThrownBy(() -> service().search(request))
                .isInstanceOf(InvalidRetailSearchRequestException.class);
        verifyNoInteractions(provider);
    }

    @Test
    void search_withLimitAboveConfiguredMax_throwsInvalidRequest() {
        RetailProductSearchRequest request = new RetailProductSearchRequest(
                Retailer.NORDSTROM, ProductCategory.SUIT, List.of(), null, null, 26);

        assertThatThrownBy(() -> service().search(request))
                .isInstanceOf(InvalidRetailSearchRequestException.class);
        verifyNoInteractions(provider);
    }

    @Test
    void search_withUnspecifiedLimit_defaultsToTenAndDelegatesToProvider() {
        RetailProductSearchRequest request = new RetailProductSearchRequest(
                Retailer.NORDSTROM, ProductCategory.SUIT, List.of(), null, null, 0);
        when(provider.search(any())).thenReturn(new RetailProductSearchResult(List.of()));

        service().search(request);

        ArgumentCaptor<RetailProductSearchRequest> captor = ArgumentCaptor.forClass(RetailProductSearchRequest.class);
        verify(provider).search(captor.capture());
        assertThat(captor.getValue().limit()).isEqualTo(10);
    }

    @Test
    void search_withKeywordsOnly_isValid_andDelegatesToProvider() {
        RetailProductSearchRequest request = new RetailProductSearchRequest(
                Retailer.NORDSTROM, null, List.of("navy", "wedding suit"), new BigDecimal("600"), "40R", 5);
        RetailProductSearchResult expected = new RetailProductSearchResult(List.of());
        when(provider.search(any())).thenReturn(expected);

        RetailProductSearchResult actual = service().search(request);

        assertThat(actual).isSameAs(expected);
    }

    @Test
    void search_whenProviderFails_propagatesWithoutSwallowing() {
        RetailProductSearchRequest request = new RetailProductSearchRequest(
                Retailer.NORDSTROM, ProductCategory.SUIT, List.of(), null, null, 5);
        when(provider.search(any())).thenThrow(new ProductSearchProviderException("boom"));

        assertThatThrownBy(() -> service().search(request))
                .isInstanceOf(ProductSearchProviderException.class)
                .hasMessage("boom");
    }
}
