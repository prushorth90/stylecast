package com.stylecast.retail;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Application service for live retail product search: validates the
 * incoming request (independent of HTTP concerns) and delegates to the
 * configured {@link RetailProductSearchProvider}. Never falls back to any
 * local/fictional catalog data - a provider failure propagates as a
 * {@link ProductSearchProviderException}.
 */
@Service
public class RetailProductSearchService {

    static final int DEFAULT_LIMIT = 10;

    private final RetailProductSearchProvider provider;
    private final RetailSearchProperties properties;

    public RetailProductSearchService(RetailProductSearchProvider provider, RetailSearchProperties properties) {
        this.provider = provider;
        this.properties = properties;
    }

    public RetailProductSearchResult search(RetailProductSearchRequest request) {
        RetailProductSearchRequest validated = validate(request);
        return provider.search(validated);
    }

    private RetailProductSearchRequest validate(RetailProductSearchRequest request) {
        if (request.retailer() == null) {
            throw new InvalidRetailSearchRequestException("retailer is required");
        }
        boolean hasCategory = request.category() != null;
        boolean hasKeywords = !request.keywords().isEmpty();
        if (!hasCategory && !hasKeywords) {
            throw new InvalidRetailSearchRequestException("At least one of category or keywords is required");
        }
        if (request.maxPrice() != null && request.maxPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidRetailSearchRequestException("maxPrice must be greater than zero");
        }

        int resolvedLimit = resolveLimit(request.limit());

        return new RetailProductSearchRequest(
                request.retailer(),
                request.category(),
                request.keywords(),
                request.maxPrice(),
                request.clothingSize(),
                resolvedLimit);
    }

    private int resolveLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        if (limit > properties.maxResultLimit()) {
            throw new InvalidRetailSearchRequestException("limit must not exceed " + properties.maxResultLimit());
        }
        return limit;
    }
}
