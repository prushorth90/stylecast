package com.stylecast.recommendation;

import com.stylecast.catalog.ProductCategory;

import java.util.List;

/**
 * The fixed, named outfit templates the deterministic engine can assemble
 * from. Each template is a small set of category "slots"; see {@link
 * TemplateSlot} for how optional coupling (e.g. SUIT vs BLAZER+TROUSERS) is
 * expressed. New templates can be added here (and wired into {@link
 * OutfitTemplateSelector}) without changing any other recommendation class.
 */
final class OutfitTemplateCatalog {

    private OutfitTemplateCatalog() {
    }

    /** Formal or wedding menswear: SUIT or BLAZER (+TROUSERS), SHIRT, SHOES, optional ACCESSORY/OUTERWEAR. */
    static final OutfitTemplate FORMAL_MENSWEAR = new OutfitTemplate(
            "FORMAL_MENSWEAR",
            List.of(
                    TemplateSlot.required("OUTER", ProductCategory.SUIT, ProductCategory.BLAZER),
                    TemplateSlot.requiredWhen("TROUSERS", ProductCategory.TROUSERS, "OUTER", ProductCategory.BLAZER),
                    TemplateSlot.required("SHIRT", ProductCategory.SHIRT),
                    TemplateSlot.required("SHOES", ProductCategory.SHOES),
                    TemplateSlot.optional("ACCESSORY", ProductCategory.ACCESSORY),
                    TemplateSlot.optional("OUTERWEAR", ProductCategory.OUTERWEAR)));

    /** Business or interview: BLAZER or SUIT (+TROUSERS), SHIRT, SHOES, optional ACCESSORY. */
    static final OutfitTemplate BUSINESS_INTERVIEW = new OutfitTemplate(
            "BUSINESS_INTERVIEW",
            List.of(
                    TemplateSlot.required("OUTER", ProductCategory.BLAZER, ProductCategory.SUIT),
                    TemplateSlot.requiredWhen("TROUSERS", ProductCategory.TROUSERS, "OUTER", ProductCategory.BLAZER),
                    TemplateSlot.required("SHIRT", ProductCategory.SHIRT),
                    TemplateSlot.required("SHOES", ProductCategory.SHOES),
                    TemplateSlot.optional("ACCESSORY", ProductCategory.ACCESSORY)));

    /** Smart casual: BLAZER, POLO, or SHIRT, TROUSERS, SHOES, optional OUTERWEAR/ACCESSORY. */
    static final OutfitTemplate SMART_CASUAL = new OutfitTemplate(
            "SMART_CASUAL",
            List.of(
                    TemplateSlot.required("TOP", ProductCategory.BLAZER, ProductCategory.POLO, ProductCategory.SHIRT),
                    TemplateSlot.required("TROUSERS", ProductCategory.TROUSERS),
                    TemplateSlot.required("SHOES", ProductCategory.SHOES),
                    TemplateSlot.optional("OUTERWEAR", ProductCategory.OUTERWEAR),
                    TemplateSlot.optional("ACCESSORY", ProductCategory.ACCESSORY)));

    /**
     * Dress-based outfit: a DRESS on its own, or a SKIRT with a separate
     * top. Exists so the engine doesn't assume every event needs a
     * menswear-style template - the catalog's DRESS/SKIRT categories are
     * supported as a first-class alternative shape.
     */
    static final OutfitTemplate DRESS_BASED = new OutfitTemplate(
            "DRESS_BASED",
            List.of(
                    TemplateSlot.required("MAIN", ProductCategory.DRESS, ProductCategory.SKIRT),
                    TemplateSlot.requiredWhen(
                            "TOP", ProductCategory.SHIRT, "MAIN", ProductCategory.SKIRT),
                    TemplateSlot.required("SHOES", ProductCategory.SHOES),
                    TemplateSlot.optional("OUTERWEAR", ProductCategory.OUTERWEAR),
                    TemplateSlot.optional("ACCESSORY", ProductCategory.ACCESSORY)));
}
