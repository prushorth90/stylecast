package com.stylecast.occasion;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Pure, I/O-free normalization shared by every {@link RequestedItem} source
 * ({@link OccasionInterpretationValidator} for AI output, {@link
 * RuleBasedOccasionClassifier} for the deterministic fallback). Never
 * throws - a phrase that fails the "must not be blank" rule simply yields
 * {@code null}, and the caller skips that candidate item entirely, the same
 * way other array fields in this package silently drop blank entries rather
 * than failing the whole classification.
 */
final class RequestedItemNormalizer {

    /** Bounds the number of search-term variants generated per item (keeps live-search cost/recall proportionate). */
    static final int MAX_SEARCH_TERMS = 6;
    static final int MAX_PHRASE_LENGTH = 200;
    static final int MAX_ACTIVITY_CONTEXT_LENGTH = 100;

    private RequestedItemNormalizer() {
    }

    /**
     * Builds a validated, normalized {@link RequestedItem}, or returns
     * {@code null} when {@code originalPhrase} is blank or {@code
     * genericCategory} is missing - callers must skip the item entirely in
     * that case rather than persisting a partially-valid one.
     */
    static RequestedItem normalize(
            String originalPhrase,
            GenericItemCategory genericCategory,
            List<String> rawSearchTerms,
            Boolean required,
            String activityContext,
            int displayOrder) {
        if (originalPhrase == null || originalPhrase.isBlank() || genericCategory == null) {
            return null;
        }

        String phrase = truncate(originalPhrase.trim(), MAX_PHRASE_LENGTH);
        List<String> searchTerms = normalizeSearchTerms(rawSearchTerms, phrase);
        String context = (activityContext == null || activityContext.isBlank())
                ? null
                : truncate(activityContext.trim(), MAX_ACTIVITY_CONTEXT_LENGTH);

        return new RequestedItem(
                UUID.randomUUID(),
                phrase,
                genericCategory,
                searchTerms,
                required == null || required,
                context,
                displayOrder);
    }

    /**
     * Trims, drops blanks, case-insensitively deduplicates (keeping the
     * first-seen casing), and bounds to {@link #MAX_SEARCH_TERMS}. Falls
     * back to {@code [originalPhrase]} when nothing valid remains, so a
     * {@link RequestedItem} always has at least one search term to drive
     * live search with.
     */
    private static List<String> normalizeSearchTerms(List<String> rawSearchTerms, String originalPhrase) {
        Map<String, String> seen = new LinkedHashMap<>();
        if (rawSearchTerms != null) {
            for (String term : rawSearchTerms) {
                if (term == null) {
                    continue;
                }
                String trimmed = term.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                String key = trimmed.toLowerCase(Locale.ROOT);
                seen.putIfAbsent(key, trimmed);
                if (seen.size() >= MAX_SEARCH_TERMS) {
                    break;
                }
            }
        }
        if (seen.isEmpty()) {
            seen.put(originalPhrase.toLowerCase(Locale.ROOT), originalPhrase);
        }
        return List.copyOf(seen.values());
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
