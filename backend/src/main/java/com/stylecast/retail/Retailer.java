package com.stylecast.retail;

/**
 * A retailer that live product search can be restricted to.
 *
 * <p>Only {@link #NORDSTROM} is supported today. Adding a new retailer later
 * requires a corresponding domain-restricted {@link RetailProductSearchProvider}
 * implementation; this enum intentionally has a single value so unsupported
 * retailer names fail JSON deserialization (HTTP 400) rather than silently
 * falling through to Nordstrom.
 */
public enum Retailer {
    NORDSTROM
}
