package com.stylecast.occasion;

import java.util.List;
import java.util.UUID;

/**
 * A single explicit product phrase extracted from a user's {@code
 * outfitRequest} (e.g. "USA soccer jersey"), preserved verbatim rather than
 * collapsed into a broad catalog category. Both {@link OpenAiOccasionClassifier}
 * (validated by {@link OccasionInterpretationValidator}) and {@link
 * RuleBasedOccasionClassifier} produce these via the shared {@link
 * RequestedItemNormalizer}, which is what guarantees {@code originalPhrase}
 * is never blank and {@code searchTerms} is always normalized/bounded.
 *
 * @param id               stable identity for this requested item within one
 *                         interpretation - used to persist/reconstruct
 *                         per-item live-search results across generations
 * @param originalPhrase   the user's own words, trimmed, never blank (e.g. "USA soccer jersey")
 * @param genericCategory  a broad, activity-agnostic category - never a
 *                         narrow catalog category that would lose meaning
 * @param searchTerms      normalized, deduplicated, bounded keyword variants used to
 *                         drive the live product search for this item; never empty
 *                         (falls back to {@code originalPhrase} itself when none are given)
 * @param required         whether this item must be found for a complete outfit
 * @param activityContext  optional open free text (e.g. "soccer", "hiking"), {@code null} if none
 * @param displayOrder     preserves the order items were mentioned/extracted in
 */
public record RequestedItem(
        UUID id,
        String originalPhrase,
        GenericItemCategory genericCategory,
        List<String> searchTerms,
        boolean required,
        String activityContext,
        int displayOrder
) {
    public RequestedItem {
        searchTerms = searchTerms == null ? List.of() : List.copyOf(searchTerms);
    }
}
