package com.stylecast.catalog;

/**
 * Style descriptor a {@link Product} is tagged with.
 *
 * Intentionally a distinct enum from {@link com.stylecast.event.styling.PreferredStyle}
 * (same vocabulary) so the catalog module stays independent of the event
 * module per the module boundaries in docs/ARCHITECTURE.md.
 */
public enum StyleTag {
    CLASSIC,
    MODERN,
    MINIMAL,
    BOLD,
    CASUAL,
    FORMAL
}
