package com.stylecast.retail.dto;

import com.stylecast.catalog.ProductCategory;
import com.stylecast.retail.RetailProductCandidate;
import com.stylecast.retail.RetailProductSource;
import com.stylecast.retail.Retailer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Response representation of a single {@link RetailProductCandidate}.
 */
public record RetailProductCandidateResponse(
        RetailProductSource source,
        Retailer retailer,
        String title,
        ProductCategory category,
        BigDecimal price,
        String currency,
        String productUrl,
        String imageUrl,
        String description,
        List<String> availableSizes,
        boolean availabilityVerified,
        Instant retrievedAt,
        String sourceCitation
) {
    public static RetailProductCandidateResponse fromDomain(RetailProductCandidate candidate) {
        return new RetailProductCandidateResponse(
                candidate.source(),
                candidate.retailer(),
                candidate.title(),
                candidate.category(),
                candidate.price(),
                candidate.currency(),
                candidate.productUrl(),
                candidate.imageUrl(),
                candidate.description(),
                candidate.availableSizes(),
                candidate.availabilityVerified(),
                candidate.retrievedAt(),
                candidate.sourceCitation());
    }
}
