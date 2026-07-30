package com.stylecast.recommendation.dto;

import com.stylecast.catalog.ProductCategory;
import com.stylecast.retail.CandidateAudience;
import com.stylecast.retail.Retailer;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Public API representation of one {@link com.stylecast.recommendation.LiveOutfitItem}.
 *
 * <p>{@code brand}/{@code price}/{@code originalPrice}/{@code currency}/
 * {@code imageUrl}/{@code color}/{@code stockText} are {@code null} (and
 * {@code availableSizes} empty) whenever StyleCast could not independently
 * confirm that field - the frontend must render the corresponding {@code
 * priceVerified}/{@code sizeVerified}/{@code availabilityVerified} flag as
 * "unverified" in that case, never substitute a guess. {@code audience} is
 * {@link CandidateAudience#UNKNOWN} whenever no trustworthy department
 * signal was found - the frontend should simply omit any department/audience
 * display in that case, never guess one.
 */
public record LiveOutfitItemResponse(
        UUID id,
        ProductCategory category,
        Retailer retailer,
        String title,
        String brand,
        String productUrl,
        String imageUrl,
        BigDecimal price,
        BigDecimal originalPrice,
        String currency,
        boolean priceVerified,
        String color,
        String requestedSize,
        List<String> availableSizes,
        boolean sizeVerified,
        String stockText,
        boolean availabilityVerified,
        CandidateAudience audience,
        String sourceCitation,
        int displayOrder) {
}
