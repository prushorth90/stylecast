package com.stylecast.occasion;

/**
 * Converts event/preference facts into a validated, structured occasion
 * interpretation.
 *
 * <p>Implementations must never invent live weather, product names, URLs,
 * prices, or inventory - see {@link OccasionClassificationInput} and
 * {@link OccasionClassificationResult} for the exact facts in and out.
 */
public interface OccasionClassifier {

    /**
     * @throws OccasionClassificationException if this classifier cannot produce a
     *                                          valid result (only thrown by
     *                                          {@link OpenAiOccasionClassifier};
     *                                          {@link RuleBasedOccasionClassifier} never throws)
     */
    OccasionClassificationResult classify(OccasionClassificationInput input);
}
