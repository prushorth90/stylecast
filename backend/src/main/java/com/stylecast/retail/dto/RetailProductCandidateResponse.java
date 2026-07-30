package com.stylecast.retail.dto;

import com.stylecast.catalog.ProductCategory;
import com.stylecast.retail.CandidateAudience;
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
        String brand,
        ProductCategory category,
        BigDecimal price,
        BigDecimal originalPrice,
        String currency,
        String productUrl,
        String imageUrl,
        String description,
        String color,
        List<String> availableSizes,
        String stockText,
        boolean priceVerified,
        boolean sizeVerified,
        boolean availabilityVerified,
        CandidateAudience audience,
        Instant retrievedAt,
        String sourceCitation
) {
    public static RetailProductCandidateResponse fromDomain(RetailProductCandidate candidate) {
        return new RetailProductCandidateResponse(
                candidate.source(),
                candidate.retailer(),
                candidate.title(),
                candidate.brand(),
                candidate.category(),
                candidate.price(),
                candidate.originalPrice(),
                candidate.currency(),
                candidate.productUrl(),
                candidate.imageUrl(),
                candidate.description(),
                candidate.color(),
                candidate.availableSizes(),
                candidate.stockText(),
                candidate.priceVerified(),
                candidate.sizeVerified(),
                candidate.availabilityVerified(),
                candidate.audience(),
                candidate.retrievedAt(),
                candidate.sourceCitation());
    }
}
