package com.stylecast.occasion;

/**
 * Which {@link OccasionClassifier} produced an {@link OccasionClassificationResult}.
 */
public enum InterpretationSource {
    /** Produced by {@link OpenAiOccasionClassifier} and passed structured-output validation. */
    AI,
    /** Produced by {@link RuleBasedOccasionClassifier}, used whenever the AI classifier is
     * unavailable, unconfigured, times out, or returns invalid output. */
    RULE_BASED_FALLBACK
}
