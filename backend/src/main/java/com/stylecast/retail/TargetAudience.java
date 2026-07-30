package com.stylecast.retail;

/**
 * The department/gender a live product search should be restricted to
 * (derived directly from the user's saved {@code shoppingDepartment}
 * styling preference), so candidate filtering never mixes men's and
 * women's products for a request that didn't explicitly ask for both (see
 * {@link CandidateAudienceClassifier}).
 *
 * <p>{@link #NO_PREFERENCE} means no department restriction at all - the
 * search adds no department keyword and no candidate is ever rejected on
 * department grounds. {@link #UNISEX} is a distinct, softer preference:
 * the search prefers gender-neutral/unisex results, but (like {@code
 * NO_PREFERENCE}) never hard-rejects a candidate on department grounds -
 * see {@link CandidateAudienceClassifier#isAcceptable}.
 */
public enum TargetAudience {
    MEN,
    WOMEN,
    UNISEX,
    NO_PREFERENCE
}
