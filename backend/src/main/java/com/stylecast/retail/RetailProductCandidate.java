package com.stylecast.retail;

import com.stylecast.catalog.ProductCategory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * A single normalized product candidate returned by a
 * {@link RetailProductSearchProvider}.
 *
 * <p>Every field the provider could not independently confirm from the
 * search result is left {@code null} (or empty for collections) rather than
 * guessed - see the "must not invent" rule in the class-level docs of
 * {@link RetailProductSearchProvider}. {@code brand}/{@code originalPrice}/
 * {@code color}/{@code stockText} and the {@code priceVerified}/{@code
 * sizeVerified} flags are populated only via {@link #withPageDetails}, once
 * a {@link ProductDetailEnricher} has independently confirmed them - never
 * set eagerly at citation-extraction time.
 *
 * @param source               how this candidate was discovered
 * @param retailer             the retailer the product page belongs to
 * @param title                the product's display title - the citation title until
 *                             {@link #withPageDetails} replaces it with a verified page name
 * @param brand                verified brand name, {@code null} until independently confirmed
 * @param category             the catalog category this candidate was searched for
 * @param price                verified current price, {@code null} until independently confirmed
 * @param originalPrice        verified original/list price (when discounted), {@code null} unless confirmed
 * @param currency             verified ISO currency code, {@code null} until independently confirmed
 * @param productUrl           the validated {@code https://nordstrom.com/...} product page URL
 * @param imageUrl             verified product image URL, {@code null} until independently confirmed
 * @param description          optional short description, {@code null} if not independently known
 * @param color                verified color, {@code null} until independently confirmed
 * @param availableSizes       sizes independently confirmed as available; never {@code null}, often empty
 * @param stockText            verified explicit stock/availability text as shown on the page (e.g. "In stock"),
 *                             {@code null} until independently confirmed
 * @param priceVerified        {@code true} only when {@code price} was independently confirmed
 * @param sizeVerified         {@code true} only when {@code availableSizes} was independently confirmed
 * @param availabilityVerified {@code true} only when {@code stockText} was independently confirmed
 * @param audience             normalized department/audience classification (see {@link CandidateAudienceClassifier}) -
 *                             {@link CandidateAudience#UNKNOWN} when no trustworthy evidence was found
 * @param retrievedAt          when this candidate was normalized by StyleCast
 * @param sourceCitation       optional short description of how the provider found this result
 */
public record RetailProductCandidate(
        RetailProductSource source,
        Retailer retailer,
        String title,
        String brand,
        ProductCategory category,
        BigDecimal price,
        BigDecimal originalPrice,
        String currency,
        String productUrl,
        String imageUrl,
        String description,
        String color,
        List<String> availableSizes,
        String stockText,
        boolean priceVerified,
        boolean sizeVerified,
        boolean availabilityVerified,
        CandidateAudience audience,
        Instant retrievedAt,
        String sourceCitation
) {
    public RetailProductCandidate {
        availableSizes = availableSizes == null ? List.of() : List.copyOf(availableSizes);
        audience = audience == null ? CandidateAudience.UNKNOWN : audience;
    }

    /**
     * Returns a copy of this candidate with fields from an independently
     * confirmed {@link ProductPageDetails} merged in. A {@code null} field
     * on {@code details} leaves the corresponding field on this candidate
     * unchanged (never overwrites a known value with a guess); verification
     * flags are only ever set {@code true} here, based solely on whether
     * {@code details} actually carried that field. {@code audience} is
     * overridden only when {@code details} independently confirmed one
     * (structured/breadcrumb evidence takes precedence over the earlier
     * title-only classification), otherwise the existing classification is
     * kept.
     */
    public RetailProductCandidate withPageDetails(ProductPageDetails details) {
        if (details == null) {
            return this;
        }
        boolean hasSizes = !details.availableSizes().isEmpty();
        boolean hasStock = details.stockText() != null && !details.stockText().isBlank();
        return new RetailProductCandidate(
                source,
                retailer,
                details.name() != null && !details.name().isBlank() ? details.name() : title,
                details.brand() != null ? details.brand() : brand,
                category,
                details.price() != null ? details.price() : price,
                details.originalPrice() != null ? details.originalPrice() : originalPrice,
                details.currency() != null ? details.currency() : currency,
                productUrl,
                details.imageUrl() != null ? details.imageUrl() : imageUrl,
                description,
                details.color() != null ? details.color() : color,
                hasSizes ? details.availableSizes() : availableSizes,
                hasStock ? details.stockText() : stockText,
                details.price() != null,
                hasSizes,
                hasStock,
                details.audience() != null ? details.audience() : audience,
                retrievedAt,
                sourceCitation);
    }
}
