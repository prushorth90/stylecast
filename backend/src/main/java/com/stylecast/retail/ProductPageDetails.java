package com.stylecast.retail;

import java.math.BigDecimal;
import java.util.List;

/**
 * Product-page details independently confirmed by a {@link ProductDetailEnricher}
 * for one candidate's {@code productUrl}, used to fill in {@link
 * RetailProductCandidate} via {@link RetailProductCandidate#withPageDetails}.
 *
 * <p>Every field is {@code null} (or empty for {@code availableSizes}) when
 * the enricher could not independently confirm it - never guessed. An
 * enricher implementation must apply its own validation (sane price range,
 * non-blank strings, etc.) before setting any field here; this record does
 * not itself re-validate.
 *
 * @param audience normalized department/audience ({@link CandidateAudience#MEN}/
 *                 {@link CandidateAudience#WOMEN}/{@link CandidateAudience#UNISEX}),
 *                 confirmed only from an explicit breadcrumb/taxonomy/department
 *                 label or explicit gender wording on the page - never {@code
 *                 null}-vs-{@link CandidateAudience#UNKNOWN} guessing, and never
 *                 inferred from an image; {@code null} when not confirmed
 */
public record ProductPageDetails(
        String brand,
        String name,
        BigDecimal price,
        BigDecimal originalPrice,
        String currency,
        String imageUrl,
        String color,
        List<String> availableSizes,
        String stockText,
        CandidateAudience audience
) {
    public ProductPageDetails {
        availableSizes = availableSizes == null ? List.of() : List.copyOf(availableSizes);
    }

    /** A details instance with every field empty/null - used when nothing could be confirmed. */
    public static ProductPageDetails empty() {
        return new ProductPageDetails(null, null, null, null, null, null, null, List.of(), null, null);
    }
}
