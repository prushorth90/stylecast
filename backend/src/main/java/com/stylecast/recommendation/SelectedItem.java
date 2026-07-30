package com.stylecast.recommendation;

import com.stylecast.catalog.ProductCategory;

/** One resolved item in an {@link OutfitCandidate}: the category slot it fills plus the chosen candidate. */
record SelectedItem(ProductCategory category, EligibleCandidate candidate) {
}
