package com.stylecast.recommendation;

import com.stylecast.catalog.Product;
import com.stylecast.catalog.ProductVariant;

import java.math.BigDecimal;

/**
 * One product+variant pairing that has already passed every per-item hard
 * constraint (active, size, stock, color, formality, weather) for a given
 * {@link RecommendationContext}, produced by {@link
 * ProductEligibilityService}. {@code effectivePrice} is always {@link
 * ProductVariant#getEffectivePrice()} - never recomputed or invented.
 */
record EligibleCandidate(Product product, ProductVariant variant, BigDecimal effectivePrice) {
}
