package com.stylecast.retail;

import java.util.regex.Pattern;

/**
 * Classifies a live product candidate's department/audience from its title
 * text, and decides whether a given classification is acceptable for a
 * requested {@link TargetAudience}.
 *
 * <p>Uses {@code \b} word boundaries so {@code "men's"} never false-matches
 * as a substring of {@code "women's"} (which literally contains the
 * characters {@code "men's"} starting at index 2, but with no word boundary
 * immediately before them).
 */
final class CandidateAudienceClassifier {

    private static final Pattern MENS_MARKER = Pattern.compile("(?i)\\bmen'?s?\\b|\\(men\\)");
    private static final Pattern WOMENS_MARKER = Pattern.compile("(?i)\\bwomen'?s?\\b|\\(women\\)");
    private static final Pattern UNISEX_MARKER = Pattern.compile("(?i)\\bunisex\\b|\\bgender[- ]neutral\\b");

    private CandidateAudienceClassifier() {
    }

    /**
     * Classifies from title text alone - the only trustworthy signal
     * available before enrichment. Never infers from an image (this
     * codebase never analyzes images at all). Returns {@link
     * CandidateAudience#UNKNOWN} when the title carries no explicit
     * marker, or conflicting markers for both departments - never guessed.
     */
    static CandidateAudience classifyFromTitle(String title) {
        if (title == null) {
            return CandidateAudience.UNKNOWN;
        }
        boolean mensMarker = MENS_MARKER.matcher(title).find();
        boolean womensMarker = WOMENS_MARKER.matcher(title).find();
        if (mensMarker && womensMarker) {
            return CandidateAudience.UNKNOWN;
        }
        if (mensMarker) {
            return CandidateAudience.MEN;
        }
        if (womensMarker) {
            return CandidateAudience.WOMEN;
        }
        if (UNISEX_MARKER.matcher(title).find()) {
            return CandidateAudience.UNISEX;
        }
        return CandidateAudience.UNKNOWN;
    }

    /**
     * @return {@code true} when a candidate classified as {@code
     * candidateAudience} is acceptable for a search that requested {@code
     * requestedDepartment}. Only ever rejects a candidate that is the
     * explicit opposite department of a {@link TargetAudience#MEN} or
     * {@link TargetAudience#WOMEN} request; {@link CandidateAudience#UNISEX}
     * and {@link CandidateAudience#UNKNOWN} are always accepted (never
     * guess a rejection), and a {@link TargetAudience#UNISEX}/{@link
     * TargetAudience#NO_PREFERENCE} request never rejects on department
     * grounds at all.
     */
    static boolean isAcceptable(CandidateAudience candidateAudience, TargetAudience requestedDepartment) {
        if (requestedDepartment == TargetAudience.MEN) {
            return candidateAudience != CandidateAudience.WOMEN;
        }
        if (requestedDepartment == TargetAudience.WOMEN) {
            return candidateAudience != CandidateAudience.MEN;
        }
        return true;
    }
}
