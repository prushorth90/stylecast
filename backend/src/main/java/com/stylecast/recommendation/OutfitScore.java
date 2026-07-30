package com.stylecast.recommendation;

/**
 * The six deterministic soft-scoring components (each bounded 0-100, see
 * {@link OutfitScorer}) plus the weighted {@code overall} score used to
 * rank outfits.
 */
record OutfitScore(
        int occasionFitScore,
        int weatherFitScore,
        int styleFitScore,
        int colorFitScore,
        int budgetEfficiencyScore,
        int completenessScore,
        int overallScore) {
}
