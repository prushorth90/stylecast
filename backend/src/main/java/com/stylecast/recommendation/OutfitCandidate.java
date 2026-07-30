package com.stylecast.recommendation;

import java.math.BigDecimal;
import java.util.List;

/**
 * One fully-assembled, not-yet-validated candidate outfit: the template it
 * was built from and its resolved items (one per required/optional slot
 * that was filled). Produced by {@link OutfitCombinationGenerator}, checked
 * by {@link HardConstraintValidator}, and scored by {@link OutfitScorer}.
 */
record OutfitCandidate(String templateName, List<SelectedItem> items) {

    BigDecimal totalPrice() {
        return items.stream()
                .map(item -> item.candidate().effectivePrice())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
