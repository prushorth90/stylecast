package com.stylecast.occasion;

/**
 * A broad, activity-agnostic garment/equipment category used to classify an
 * explicitly {@link RequestedItem} without forcing it into a narrower
 * catalog {@link com.stylecast.catalog.ProductCategory} value (which would
 * often lose meaning - e.g. a soccer jersey is not a dress SHIRT). This is
 * deliberately a small, fixed set: new activities/sports are supported via
 * free-text {@code originalPhrase}/{@code searchTerms}/{@code
 * activityContext}, never by adding another enum value here.
 */
public enum GenericItemCategory {
    TOP,
    BOTTOM,
    ONE_PIECE,
    FOOTWEAR,
    OUTERWEAR,
    ACCESSORY,
    EQUIPMENT,
    OTHER
}
