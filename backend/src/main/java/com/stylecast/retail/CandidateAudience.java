package com.stylecast.retail;

/**
 * Normalized department/audience classification for a single live product
 * candidate, determined from trustworthy evidence only (title text, and -
 * when an enrichment call independently confirms it - explicit page
 * breadcrumb/taxonomy or structured department metadata). Never inferred
 * from an image alone.
 */
public enum CandidateAudience {
    MEN,
    WOMEN,
    UNISEX,

    /** No explicit, trustworthy department signal was found - never guessed. */
    UNKNOWN
}
