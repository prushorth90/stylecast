package com.stylecast.recommendation;

import com.stylecast.catalog.ProductCategory;

import java.util.List;

/**
 * One slot in an {@link OutfitTemplate}: a role that must (or may) be filled
 * by a product from one of a small set of interchangeable categories.
 *
 * <p>{@code alternatives} lets a slot accept more than one category as an
 * equivalent choice - e.g. a formal top can be a {@code SUIT} (which already
 * includes trousers) or a {@code BLAZER} (which needs separate trousers).
 * That coupling is expressed with {@code dependsOnSlot}/{@code
 * requiredWhenDependencyIn}: this slot is only required when the referenced
 * slot resolved to one of the given categories; otherwise it is skipped
 * entirely (not even attempted as optional), so a suit-based outfit never
 * gets a redundant pair of trousers.
 */
public record TemplateSlot(
        String name,
        List<ProductCategory> alternatives,
        boolean required,
        String dependsOnSlot,
        List<ProductCategory> requiredWhenDependencyIn) {

    public TemplateSlot {
        if (alternatives == null || alternatives.isEmpty()) {
            throw new IllegalArgumentException("alternatives must not be empty");
        }
        requiredWhenDependencyIn = requiredWhenDependencyIn == null ? List.of() : List.copyOf(requiredWhenDependencyIn);
    }

    public static TemplateSlot required(String name, ProductCategory... alternatives) {
        return new TemplateSlot(name, List.of(alternatives), true, null, null);
    }

    public static TemplateSlot optional(String name, ProductCategory... alternatives) {
        return new TemplateSlot(name, List.of(alternatives), false, null, null);
    }

    /**
     * A slot that is only required when {@code dependsOnSlot} resolved to
     * one of {@code triggerCategories} (e.g. TROUSERS is only required when
     * the "OUTER" slot resolved to BLAZER, not SUIT).
     */
    public static TemplateSlot requiredWhen(
            String name, ProductCategory alternative, String dependsOnSlot, ProductCategory... triggerCategories) {
        return new TemplateSlot(name, List.of(alternative), false, dependsOnSlot, List.of(triggerCategories));
    }

    public boolean isConditional() {
        return dependsOnSlot != null;
    }
}
