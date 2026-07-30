package com.stylecast.recommendation;

import java.util.List;

/**
 * A named, ordered set of {@link TemplateSlot}s describing one outfit
 * "shape" (e.g. suit-or-blazer menswear vs. a dress-based outfit). Adding a
 * new outfit shape only requires adding another {@code OutfitTemplate} to
 * {@link OutfitTemplateCatalog} and a selection rule in {@link
 * OutfitTemplateSelector} - {@link OutfitCombinationGenerator} itself has no
 * knowledge of specific categories or occasions.
 */
public record OutfitTemplate(String name, List<TemplateSlot> slots) {

    public OutfitTemplate {
        if (slots == null || slots.isEmpty()) {
            throw new IllegalArgumentException("slots must not be empty");
        }
        slots = List.copyOf(slots);
    }
}
